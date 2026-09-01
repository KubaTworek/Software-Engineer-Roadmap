package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;

import java.util.Objects;

/**
 * Example wrapper around a Redis client.
 *
 * In production, Redis may already be covered by auto-instrumentation.
 * This wrapper demonstrates how to add explicit manual semantics if needed.
 */
public final class TracedRedisClient {

    private final CheckoutSpanFactory spanFactory;
    private final RedisGateway redisGateway;

    public TracedRedisClient(CheckoutSpanFactory spanFactory, RedisGateway redisGateway) {
        this.spanFactory = Objects.requireNonNull(spanFactory, "spanFactory must not be null");
        this.redisGateway = Objects.requireNonNull(redisGateway, "redisGateway must not be null");
    }

    public String getOrderFromCache(String cacheKey) {
        try (SpanScope spanScope = spanFactory.startRedisGetSpan("0")) {
            try {
                String value = redisGateway.get(requireNonBlank(cacheKey, "cacheKey"));

                Span span = spanScope.span();
                span.setAttribute(TracingAttributes.CACHE_HIT, value != null);

                return value;
            } catch (RuntimeException exception) {
                SpanErrorHandler.recordException(spanScope.span(), exception);
                throw exception;
            }
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * Minimal Redis abstraction used to keep tracing code independent from a specific Redis library.
     */
    public interface RedisGateway {
        String get(String key);
    }
}
