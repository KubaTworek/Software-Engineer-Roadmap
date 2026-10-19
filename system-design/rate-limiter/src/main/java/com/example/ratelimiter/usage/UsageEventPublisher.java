package com.example.ratelimiter.usage;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** Best-effort audit stream; it is explicitly not the billing source of truth. */
@Component
@Profile("!durable")
public class UsageEventPublisher implements UsageEventSink {
    private static final Logger log = LoggerFactory.getLogger(UsageEventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final RateLimiterProperties properties;
    private final ObjectMapper json;
    private final MeterRegistry metrics;

    public UsageEventPublisher(KafkaTemplate<String, String> kafka, RateLimiterProperties properties,
            ObjectMapper json, MeterRegistry metrics) {
        this.kafka = kafka; this.properties = properties; this.json = json; this.metrics = metrics;
    }

    @Override
    public void publish(RequestContext context, RateLimitDecision decision) {
        if (!properties.getUsageEvents().isEnabled()) return;
        UsageEvent event = new UsageEvent(UUID.randomUUID().toString(), Instant.ofEpochMilli(context.timestampMs()),
                context.tenantId(), context.userId(), context.apiKeyHash(), context.path(), context.method(),
                decision.allowed());
        try {
            String payload = json.writeValueAsString(event);
            String key = context.tenantId() == null ? context.principalKey() : context.tenantId();
            kafka.send(properties.getUsageEvents().getKafkaTopic(), key, payload).whenComplete((result, failure) -> {
                String status = failure == null ? "success" : "failure";
                metrics.counter("rate_limiter.usage.publish", "result", status).increment();
                if (failure != null) log.warn("usage_event_publish_failed eventId={} error={}", event.eventId(), failure.toString());
            });
        } catch (JsonProcessingException exception) {
            recordImmediateFailure(event, "serialization-failure", exception);
        } catch (RuntimeException exception) {
            // KafkaTemplate może odrzucić send synchronicznie, zanim zwróci future.
            // Strumień jest best effort, więc nie zmieniamy ukończonej decyzji limitera.
            recordImmediateFailure(event, "send-failure", exception);
        }
    }

    private void recordImmediateFailure(UsageEvent event, String result, Exception exception) {
        metrics.counter("rate_limiter.usage.publish", "result", result).increment();
        log.warn("usage_event_publish_failed eventId={} result={} error={}",
                event.eventId(), result, exception.toString());
    }

    public record UsageEvent(String eventId, Instant occurredAt, String tenantId, String userId,
                             String apiKeyHash, String path, String method, boolean allowed) {}
}
