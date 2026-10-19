package com.example.ratelimiter.usage;

import com.example.ratelimiter.config.RateLimiterProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Leased outbox relay. Publish-before-mark can duplicate an event after a
 * crash, therefore eventId remains the downstream idempotency key.
 */
@Component
@Profile("durable")
public class UsageOutboxRelay {
    private static final int MAX_ATTEMPTS = 10;
    private final String owner = "relay-" + UUID.randomUUID();
    private final JdbcUsageOutbox outbox;
    private final KafkaTemplate<String, String> kafka;
    private final RateLimiterProperties properties;
    private final MeterRegistry metrics;

    public UsageOutboxRelay(JdbcUsageOutbox outbox, KafkaTemplate<String, String> kafka,
            RateLimiterProperties properties, MeterRegistry metrics) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${rate-limiter.durable.outbox-poll-ms:500}")
    public void relay() {
        Instant now = Instant.now();
        for (var event : outbox.claim(owner, 100, now, Duration.ofSeconds(30))) {
            try {
                kafka.send(properties.getUsageEvents().getKafkaTopic(), event.tenantId(), event.payload())
                        .get(5, TimeUnit.SECONDS);
                outbox.markPublished(event.eventId(), owner, Instant.now());
                metrics.counter("rate_limiter.usage.outbox", "result", "published").increment();
            } catch (Exception exception) {
                metrics.counter("rate_limiter.usage.outbox", "result", "retry").increment();
                if (event.attempt() >= MAX_ATTEMPTS) {
                    outbox.markDead(event.eventId(), owner, exception.toString());
                    metrics.counter("rate_limiter.usage.outbox", "result", "dead").increment();
                }
                // Before MAX_ATTEMPTS the lease expires and another poll can retry.
            }
        }
    }
}
