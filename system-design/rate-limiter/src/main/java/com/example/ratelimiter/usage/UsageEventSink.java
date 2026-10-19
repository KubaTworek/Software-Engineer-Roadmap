package com.example.ratelimiter.usage;

import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RequestContext;

/** Boundary selecting best-effort Kafka or durable receipt/outbox delivery. */
public interface UsageEventSink {
    void publish(RequestContext context, RateLimitDecision decision);
}
