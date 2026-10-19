package com.example.ratelimiter.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.LocalFallbackLimiter;
import com.example.ratelimiter.core.RateLimiterEngine;
import com.example.ratelimiter.core.RedisTokenBucketLimiter;
import com.example.ratelimiter.core.RequestContext;
import com.example.ratelimiter.core.RuleMatcher;
import com.example.ratelimiter.quota.QuotaService;
import com.example.ratelimiter.usage.UsageEventPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.common.KeyValue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RateLimiterObservationTest {
    @Test
    void decisionCreatesLowCardinalityObservationForOtelBridge() {
        AtomicReference<Observation.Context> stopped = new AtomicReference<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override public void onStop(Observation.Context context) { stopped.set(context); }
            @Override public boolean supportsContext(Observation.Context context) { return true; }
        });
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getQuotas().setEnabled(false);
        properties.getUsageEvents().setEnabled(false);
        RuleMatcher matcher = mock(RuleMatcher.class);
        when(matcher.match(any())).thenReturn(List.of());
        RateLimiterEngine engine = new RateLimiterEngine(matcher, mock(RedisTokenBucketLimiter.class),
                new LocalFallbackLimiter(properties), properties, mock(UsageEventPublisher.class),
                mock(QuotaService.class), new SimpleMeterRegistry(), registry);

        engine.check(new RequestContext("GET", "/api/users", "203.0.113.1", null,
                null, null, "FREE", System.currentTimeMillis()));

        assertThat(stopped.get().getName()).isEqualTo("rate-limiter.decision");
        assertThat(stopped.get().getLowCardinalityKeyValues())
                .contains(KeyValue.of("http.method", "GET"), KeyValue.of("decision", "allowed"));
    }
}
