package com.example.urlshortener.outbox;

import com.example.urlshortener.service.UrlProjectionUpdater;
import com.example.urlshortener.storage.UrlLookupRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** At-least-once relay; duplicate projection writes are safe because records carry source versions. */
@Component
@ConditionalOnProperty(name = "app.projection-outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class UrlProjectionOutboxRelay {
    private final UrlProjectionUpdater updater;
    private final ObjectMapper objectMapper;
    private final UrlProjectionOutboxClaims claims;
    private final int maxAttempts;

    public UrlProjectionOutboxRelay(UrlProjectionUpdater updater, ObjectMapper objectMapper,
            UrlProjectionOutboxClaims claims,
            @org.springframework.beans.factory.annotation.Value("${app.projection-outbox.max-attempts:8}") int maxAttempts) {
        this.updater = updater;
        this.objectMapper = objectMapper;
        this.claims = claims;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.projection-outbox.poll-delay:PT1S}")
    public void relay() {
        for (UrlProjectionOutboxMessage message : claims.claim()) {
            try {
                UrlLookupRecord record = objectMapper.readValue(message.getPayloadJson(), UrlLookupRecord.class);
                if (!updater.deliver(message.getEventId(), record)) {
                    scheduleRetry(message, "projection target unavailable");
                }
            } catch (JsonProcessingException poison) {
                claims.deadLetter(message.getEventId(), "invalid payload: " + poison.getOriginalMessage());
            }
        }
    }

    private void scheduleRetry(UrlProjectionOutboxMessage message, String error) {
        if (message.getAttempts() >= maxAttempts) {
            claims.deadLetter(message.getEventId(), error);
            return;
        }
        long seconds = Math.min(300, 1L << Math.min(message.getAttempts(), 8));
        claims.retry(message.getEventId(), java.time.Duration.ofSeconds(seconds), error);
    }
}
