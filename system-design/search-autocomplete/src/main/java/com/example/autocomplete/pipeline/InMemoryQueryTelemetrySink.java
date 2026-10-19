package com.example.autocomplete.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Bounded simulation telemetry sink. Dowodzi, że do granicy eventowej dociera
 * już zanonimizowany rekord; nie udaje trwałego brokera ani analytics warehouse.
 */
@Component
public class InMemoryQueryTelemetrySink implements QueryTelemetrySink {
    private final Cache<UUID, QueryEvent> events = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    @Override
    public void record(QueryEvent event) {
        events.put(UUID.randomUUID(), event);
    }

    public long size() {
        return events.estimatedSize();
    }
}
