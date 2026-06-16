package com.example.observability.server.fulltext;

import com.example.observability.server.model.LogEventDto;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class FullTextIndexService {
    private static final Pattern SPLIT = Pattern.compile("[^a-zA-Z0-9_\\-.:]+");
    private final FullTextIndexProperties properties;
    private final TelemetryRepository repository;

    public FullTextIndexService(FullTextIndexProperties properties, TelemetryRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public void indexBatch(String tenantId, List<LogEventDto> logs) {
        if (!properties.isEnabled() || logs == null || logs.isEmpty()) return;
        Map<IndexKey, TermStats> aggregate = new HashMap<>();
        for (LogEventDto log : logs) {
            Instant ts = log.getTimestamp() == null ? Instant.now() : log.getTimestamp();
            Instant bucket = ts.truncatedTo(ChronoUnit.HOURS);
            Set<String> terms = terms(log.getMessage());
            int used = 0;
            for (String term : terms) {
                if (used++ >= properties.getMaxTermsPerLog()) break;
                IndexKey key = new IndexKey(tenantId, safe(log.getService()), safe(log.getLevel()).toUpperCase(Locale.ROOT), bucket, term);
                aggregate.computeIfAbsent(key, ignored -> new TermStats()).add(safe(log.getTraceId()));
            }
        }
        repository.upsertFullTextTerms(aggregate);
    }

    public FullTextSearchPlan plan(String tenantId, String service, String level, String query, Instant start, Instant end) {
        List<String> terms = new ArrayList<>(terms(query));
        List<String> candidateBuckets = repository.lookupFullTextBuckets(tenantId, service, level, terms, start, end);
        return new FullTextSearchPlan(properties.isEnabled(), terms, candidateBuckets, candidateBuckets.isEmpty() ? "fallback-scan" : "term-index");
    }

    private Set<String> terms(String message) {
        if (message == null || message.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String raw : SPLIT.split(message.toLowerCase(Locale.ROOT))) {
            if (raw.length() >= properties.getMinTermLength() && !raw.chars().allMatch(Character::isDigit))
                out.add(raw);
        }
        return out;
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    public record IndexKey(String tenantId, String service, String level, Instant bucketStart, String term) {
    }

    public static class TermStats {
        private long count = 0;
        private final LinkedHashSet<String> traceIds = new LinkedHashSet<>();

        public void add(String traceId) {
            count++;
            if (traceId != null && !traceId.isBlank() && traceIds.size() < 10) traceIds.add(traceId);
        }

        public long count() {
            return count;
        }

        public List<String> sampleTraceIds() {
            return new ArrayList<>(traceIds);
        }
    }

    public record FullTextSearchPlan(boolean enabled, List<String> terms, List<String> candidateBuckets,
                                     String strategy) {
    }
}
