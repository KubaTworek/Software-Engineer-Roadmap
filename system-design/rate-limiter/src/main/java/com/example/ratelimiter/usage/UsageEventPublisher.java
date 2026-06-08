package com.example.ratelimiter.usage;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class UsageEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(UsageEventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RateLimiterProperties properties;

    public UsageEventPublisher(KafkaTemplate<String, String> kafkaTemplate, RateLimiterProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(RequestContext ctx, RateLimitDecision decision) {
        if (!properties.getUsageEvents().isEnabled()) return;
        String json = "{" +
                "\"timestamp\":\"" + Instant.ofEpochMilli(ctx.timestampMs()) + "\"," +
                "\"tenantId\":\"" + nullSafe(ctx.tenantId()) + "\"," +
                "\"userId\":\"" + nullSafe(ctx.userId()) + "\"," +
                "\"apiKeyHash\":\"" + nullSafe(ctx.apiKeyHash()) + "\"," +
                "\"path\":\"" + ctx.path() + "\"," +
                "\"method\":\"" + ctx.method() + "\"," +
                "\"allowed\":" + decision.allowed() +
                "}";
        try {
            kafkaTemplate.send(properties.getUsageEvents().getKafkaTopic(), ctx.tenantId() == null ? ctx.principalKey() : ctx.tenantId(), json);
        } catch (Exception ex) {
            log.warn("usage_event_publish_failed principal={} error={}", ctx.principalKey(), ex.toString());
        }
    }

    private String nullSafe(String value) { return value == null ? "" : value; }
}
