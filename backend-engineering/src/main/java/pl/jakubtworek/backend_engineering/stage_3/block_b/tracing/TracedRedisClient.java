package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import io.opentelemetry.api.trace.Span;

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
        this.spanFactory = spanFactory;
        this.redisGateway = redisGateway;
    }

    public String getOrderFromCache(String cacheKey) {
        try (SpanScope spanScope = spanFactory.startRedisGetSpan("0")) {
            String value = redisGateway.get(cacheKey);

            Span span = spanScope.span();
            span.setAttribute(TracingAttributes.CACHE_HIT, value != null);

            return value;
        } catch (RuntimeException exception) {
            SpanErrorHandler.recordException(Span.current(), exception);
            throw exception;
        }
    }

    /**
     * Minimal Redis abstraction used to keep tracing code independent from a specific Redis library.
     */
    public interface RedisGateway {
        String get(String key);
    }
}