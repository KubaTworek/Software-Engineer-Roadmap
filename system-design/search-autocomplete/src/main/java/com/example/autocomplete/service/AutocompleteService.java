package com.example.autocomplete.service;

import com.example.autocomplete.abtest.ExperimentAssignmentService;
import com.example.autocomplete.abtest.ExperimentVariant;
import com.example.autocomplete.abuse.AbuseDetectionService;
import com.example.autocomplete.cache.AutocompleteCache;
import com.example.autocomplete.index.*;
import com.example.autocomplete.language.LanguageDetector;
import com.example.autocomplete.model.*;
import com.example.autocomplete.personalization.SearchHistoryStore;
import com.example.autocomplete.pipeline.EventSanitizer;
import com.example.autocomplete.pipeline.QueryEvent;
import com.example.autocomplete.pipeline.QueryTelemetrySink;
import com.example.autocomplete.policy.SafetyPolicyFilter;
import com.example.autocomplete.ranking.SuggestionRanker;
import com.example.autocomplete.rollout.IndexRegistry;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kanoniczny serving flow: validate → normalize → abuse gate → candidate cache
 * → snapshot/delta/fuzzy retrieval → dedup → rank → final policy gate.
 *
 * Najważniejsza reguła znajduje się na końcu, nie tylko przy ingestion: żadna
 * ścieżka (w tym cache hit) nie może ominąć aktualnej polityki bezpieczeństwa.
 */
@Service
public class AutocompleteService {
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 20;
    private static final int CANDIDATE_MULTIPLIER = 10;

    private final IndexRegistry registry;
    private final RealtimeDeltaIndex deltaIndex;
    private final SuggestionRanker ranker;
    private final TextNormalizer normalizer;
    private final CanonicalKeyGenerator keyGenerator;
    private final LanguageDetector languageDetector;
    private final SafetyPolicyFilter safetyFilter;
    private final AbuseDetectionService abuseDetection;
    private final ExperimentAssignmentService experimentService;
    private final SearchHistoryStore historyStore;
    private final AutocompleteCache cache;
    private final EventSanitizer eventSanitizer;
    private final QueryTelemetrySink telemetrySink;
    private final Clock clock;

    private final MeterRegistry meterRegistry;
    private final Timer requestTimer;
    private final Counter blockedCounter;
    private final Counter cacheHitCounter;
    private final Counter zeroResultCounter;
    private final DistributionSummary candidateCount;

    public AutocompleteService(IndexRegistry registry, RealtimeDeltaIndex deltaIndex, SuggestionRanker ranker,
                               TextNormalizer normalizer, CanonicalKeyGenerator keyGenerator,
                               LanguageDetector languageDetector, SafetyPolicyFilter safetyFilter,
                               AbuseDetectionService abuseDetection, ExperimentAssignmentService experimentService,
                               SearchHistoryStore historyStore, AutocompleteCache cache,
                               EventSanitizer eventSanitizer, QueryTelemetrySink telemetrySink,
                               Clock clock, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.deltaIndex = deltaIndex;
        this.ranker = ranker;
        this.normalizer = normalizer;
        this.keyGenerator = keyGenerator;
        this.languageDetector = languageDetector;
        this.safetyFilter = safetyFilter;
        this.abuseDetection = abuseDetection;
        this.experimentService = experimentService;
        this.historyStore = historyStore;
        this.cache = cache;
        this.eventSanitizer = eventSanitizer;
        this.telemetrySink = telemetrySink;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.requestTimer = Timer.builder("autocomplete.request.latency")
                .publishPercentileHistogram().register(meterRegistry);
        this.blockedCounter = meterRegistry.counter("autocomplete.requests", "outcome", "abuse_blocked");
        this.cacheHitCounter = meterRegistry.counter("autocomplete.candidate_cache", "result", "hit");
        this.zeroResultCounter = meterRegistry.counter("autocomplete.requests", "outcome", "zero_results");
        this.candidateCount = DistributionSummary.builder("autocomplete.candidates")
                .description("Candidate count after merge and current policy")
                .register(meterRegistry);
    }

    public AutocompleteResult autocomplete(String query, Integer requestedLimit, String userId, String sessionId,
                                           String locale, String country, String category, String clientIp) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long started = System.nanoTime();
        try {
            int limit = sanitizeLimit(requestedLimit);
            String normalized = normalizer.normalize(query);
            String detectedLocale = languageDetector.detect(query, locale);
            AutocompleteContext context = new AutocompleteContext(
                    userId, sessionId, detectedLocale, country, category, query, normalized, clientIp
            );

            // Telemetria opuszcza request path dopiero po pseudonimizacji i redakcji PII.
            telemetrySink.record(eventSanitizer.sanitize(new QueryEvent(
                    context.safeUserId(), context.safeSessionId(), query, context.safeLocale(),
                    context.safeCountry(), context.safeClientIp(), clock.instant()
            )));

            if (!abuseDetection.isAllowed(context)) {
                blockedCounter.increment();
                return empty("ABUSE_BLOCKED", safeActiveVersion(), ExperimentVariant.CONTROL, started);
            }

            String experimentSubject = "anonymous".equals(context.safeUserId())
                    ? "session:" + context.safeSessionId()
                    : context.safeUserId();
            ExperimentVariant variant = experimentService.assign(experimentSubject, "ranking-v1");
            AutocompleteIndex activeIndex = registry.active();
            if (normalized.length() < 2) {
                zeroResultCounter.increment();
                return empty("TOO_SHORT", activeIndex.version(), variant, started);
            }

            int candidateLimit = limit * CANDIDATE_MULTIPLIER;
            String cacheKey = cache.candidateKey(normalized, context.safeLocale(), context.safeCountry(), category,
                    activeIndex.version(), deltaIndex.version(), safetyFilter.version(), candidateLimit);

            Optional<List<Suggestion>> cached = cache.getCandidates(cacheKey);
            List<Suggestion> candidates;
            String cacheStatus;
            if (cached.isPresent()) {
                cacheHitCounter.increment();
                candidates = cached.get();
                cacheStatus = "CANDIDATE_HIT";
            } else {
                candidates = retrieve(activeIndex, normalized, context, candidateLimit);
                cache.putCandidates(cacheKey, candidates);
                cacheStatus = "CANDIDATE_MISS";
            }

            // Defense in depth: policy jest ponawiana nawet po cache hit. Gdy polityka
            // zmieni się pomiędzy lookupem i odpowiedzią, stara sugestia nie wycieknie.
            candidates = candidates.stream()
                    .filter(suggestion -> safetyFilter.evaluate(suggestion).allowed())
                    .toList();
            candidateCount.record(candidates.size());

            List<RankedSuggestion> ranked = ranker.rank(
                            candidates, context, activeIndex.stats().maxPopularity(), limit, variant)
                    .stream()
                    .filter(result -> safetyFilter.evaluate(result.suggestion()).allowed())
                    .toList();

            historyStore.recordSessionQuery(context.safeSessionId(), normalized);
            if (ranked.isEmpty()) zeroResultCounter.increment();
            return new AutocompleteResult(ranked, null, cacheStatus, activeIndex.version(), variant, elapsedMillis(started));
        } finally {
            sample.stop(requestTimer);
        }
    }

    private List<Suggestion> retrieve(AutocompleteIndex index, String normalized,
                                      AutocompleteContext context, int candidateLimit) {
        List<Suggestion> base = index.candidates(normalized, candidateLimit);
        List<Suggestion> delta = deltaIndex.candidates(normalized, Math.min(candidateLimit, 40));
        if (base.isEmpty() && delta.isEmpty()) {
            base = index.fuzzyCandidates(normalized, normalized.length() <= 5 ? 1 : 2, candidateLimit);
        }

        // Delta ma pierwszeństwo przed snapshotem dla tego samego canonical key.
        Map<String, Suggestion> merged = new LinkedHashMap<>();
        base.forEach(suggestion -> merged.put(keyGenerator.canonicalKey(suggestion.displayText()), suggestion));
        delta.forEach(suggestion -> merged.put(keyGenerator.canonicalKey(suggestion.displayText()), suggestion));

        return merged.values().stream()
                .filter(suggestion -> matchesLocale(suggestion, context))
                .filter(suggestion -> safetyFilter.evaluate(suggestion).allowed())
                .limit(candidateLimit)
                .toList();
    }

    private boolean matchesLocale(Suggestion suggestion, AutocompleteContext context) {
        boolean locale = suggestion.locales().isEmpty() || suggestion.locales().contains(context.safeLocale());
        boolean country = suggestion.countries().isEmpty() || suggestion.countries().contains(context.safeCountry());
        return locale && country;
    }

    private AutocompleteResult empty(String status, String indexVersion, ExperimentVariant variant, long started) {
        return new AutocompleteResult(List.of(), null, status, indexVersion, variant, elapsedMillis(started));
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safeActiveVersion() {
        try {
            return registry.activeVersion();
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    public int sanitizeLimit(Integer limit) {
        return limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    public IndexStats indexStats() {
        return registry.active().stats();
    }

    public record AutocompleteResult(List<RankedSuggestion> suggestions, String correctedQuery,
                                     String cacheStatus, String indexVersion,
                                     ExperimentVariant experimentVariant, long latencyMs) {}
}
