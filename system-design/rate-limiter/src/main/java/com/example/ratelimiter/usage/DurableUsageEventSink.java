package com.example.ratelimiter.usage;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RequestContext;
import com.example.ratelimiter.usage.JdbcUsageOutbox.UsageReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Durable profile: receipt and publication intent enter PostgreSQL atomically. */
@Component
@Profile("durable")
public class DurableUsageEventSink implements UsageEventSink {
    private final JdbcUsageOutbox outbox;
    private final ObjectMapper json;
    private final RateLimiterProperties properties;
    private final MeterRegistry metrics;

    public DurableUsageEventSink(JdbcUsageOutbox outbox, ObjectMapper json,
            RateLimiterProperties properties, MeterRegistry metrics) {
        this.outbox = outbox;
        this.json = json;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public void publish(RequestContext context, RateLimitDecision decision) {
        if (!properties.getUsageEvents().isEnabled()) return;
        UUID eventId = UUID.randomUUID();
        try {
            String payload = json.writeValueAsString(Map.of(
                    "eventId", eventId.toString(),
                    "occurredAt", Instant.ofEpochMilli(context.timestampMs()).toString(),
                    "principal", context.principalKey(),
                    "path", context.path(),
                    "method", context.method(),
                    "allowed", decision.allowed()));
            String accountingKey = context.tenantId() == null ? context.principalKey() : context.tenantId();
            outbox.record(new UsageReceipt(eventId, accountingKey, 1,
                    Instant.ofEpochMilli(context.timestampMs()), payload));
            metrics.counter("rate_limiter.usage.receipt", "result", "stored").increment();
        } catch (Exception exception) {
            // The HTTP admission decision has already happened. We expose the
            // accounting gap instead of changing an allowed request into 500.
            metrics.counter("rate_limiter.usage.receipt", "result", "failure").increment();
        }
    }
}
