package com.example.ratelimiter.observability;

import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.core.RedisAvailabilityGate;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Eksportuje stan, którego nie da się poprawnie odtworzyć wyłącznie z liczników.
 * Tagi są celowo stałe, aby sam limiter nie powodował eksplozji cardinality.
 */
@Component
public class RateLimiterMetrics {
    public RateLimiterMetrics(
            MeterRegistry registry,
            RedisAvailabilityGate redisGate,
            DynamicConfigService config
    ) {
        Gauge.builder("rate_limiter.redis.circuit_open", redisGate, gate -> gate.isOpen() ? 1 : 0)
                .description("1, gdy szybka ścieżka Redis jest chwilowo odcięta")
                .register(registry);
        Gauge.builder("rate_limiter.config.version", config, DynamicConfigService::version)
                .description("Wersja lokalnego snapshotu reguł")
                .register(registry);
    }
}
