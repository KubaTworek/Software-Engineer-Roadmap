package com.example.autocomplete.feedback;

import com.example.autocomplete.privacy.QueryPrivacyService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Idempotentna symulacja wejścia eventowego. Unikalne eventId chroni przed
 * podwójnym naliczeniem impression/click po retry klienta.
 */
@Component
public class FeedbackStore {
    private final Cache<String, FeedbackEvent> events = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();
    private final QueryPrivacyService privacy;
    private final Clock clock;

    public FeedbackStore(QueryPrivacyService privacy, Clock clock) {
        this.privacy = privacy;
        this.clock = clock;
    }

    public boolean record(String eventId, FeedbackType type, String userId, String sessionId,
                          String query, List<String> suggestionIds, String clientIp) {
        FeedbackEvent sanitized = new FeedbackEvent(
                eventId, type, privacy.hashUserId(userId), privacy.hashUserId("session:" + sessionId),
                privacy.redactPii(query), List.copyOf(suggestionIds), privacy.hashIp(clientIp),
                clock.instant(), 1
        );
        return events.asMap().putIfAbsent(eventId, sanitized) == null;
    }

    public long size() {
        return events.estimatedSize();
    }
}
