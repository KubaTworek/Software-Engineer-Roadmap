package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.CacheAsideService;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.InMemoryTtlCache;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.TtlJitter;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation.ProductPage;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.ratelimit.RateLimitDecision;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreaker;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.CircuitBreakerState;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.RetryExecutor;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutConfig;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience.TimeoutExecutor;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.remote.ResilientRemoteClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductApiServiceTest {

    @Test
    void composesRateLimitCacheAndResilientPaymentWithoutDuplicatingMechanisms() throws Exception {
        AtomicInteger productLoads = new AtomicInteger();
        CacheAsideService<String, ProductPage> cache = new CacheAsideService<>(
                new InMemoryTtlCache<>(),
                productId -> {
                    productLoads.incrementAndGet();
                    return new ProductPage(productId, "Keyboard", "Mechanical", List.of(), false);
                },
                Duration.ofMinutes(5),
                new TtlJitter(Duration.ZERO)
        );
        AtomicInteger paymentCalls = new AtomicInteger();
        CircuitBreaker paymentBreaker = new CircuitBreaker("payments", 1, Duration.ofMinutes(1));

        try (TimeoutExecutor timeout = new TimeoutExecutor(Executors.newSingleThreadExecutor())) {
            ResilientRemoteClient resilientPayment = new ResilientRemoteClient(
                    paymentBreaker,
                    new RetryExecutor(
                            3,
                            Duration.ofMillis(1),
                            Duration.ofMillis(1),
                            failure -> failure instanceof IllegalStateException
                    ),
                    timeout,
                    new TimeoutConfig(Duration.ofMillis(50), Duration.ofMillis(100))
            );
            ProductApiService api = new ProductApiService(
                    ignored -> RateLimitDecision.permitted(),
                    cache,
                    resilientPayment,
                    paymentId -> {
                        if (paymentCalls.incrementAndGet() < 3) {
                            throw new IllegalStateException("transient payment failure");
                        }
                        return "reserved:" + paymentId;
                    }
            );

            assertThat(api.getProductPage("tenant-1", "product-1").name()).isEqualTo("Keyboard");
            assertThat(api.getProductPage("tenant-1", "product-1").name()).isEqualTo("Keyboard");
            assertThat(productLoads).hasValue(1);

            assertThat(api.reservePayment("tenant-1", "payment-1")).isEqualTo("reserved:payment-1");
            assertThat(paymentCalls).hasValue(3);
            assertThat(paymentBreaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
        }
    }

    @Test
    void rateLimitRejectsBeforeCacheOrPaymentDependency() throws Exception {
        AtomicInteger dependencyCalls = new AtomicInteger();
        CacheAsideService<String, ProductPage> cache = new CacheAsideService<>(
                new InMemoryTtlCache<>(),
                productId -> {
                    dependencyCalls.incrementAndGet();
                    return new ProductPage(productId, "name", "description", List.of(), false);
                },
                Duration.ofMinutes(1),
                new TtlJitter(Duration.ZERO)
        );

        try (TimeoutExecutor timeout = new TimeoutExecutor(Executors.newSingleThreadExecutor())) {
            ProductApiService api = new ProductApiService(
                    ignored -> RateLimitDecision.rejected(Duration.ofSeconds(2)),
                    cache,
                    new ResilientRemoteClient(
                            new CircuitBreaker("payments", 1, Duration.ofMinutes(1)),
                            new RetryExecutor(1, Duration.ofMillis(1), Duration.ofMillis(1), ignored -> false),
                            timeout,
                            new TimeoutConfig(Duration.ofMillis(50), Duration.ofMillis(100))
                    ),
                    paymentId -> {
                        dependencyCalls.incrementAndGet();
                        return "must-not-run";
                    }
            );

            assertThatThrownBy(() -> api.getProductPage("tenant-1", "product-1"))
                    .isInstanceOf(TooManyRequestsException.class)
                    .extracting("retryAfterSeconds")
                    .isEqualTo(2L);
            assertThatThrownBy(() -> api.reservePayment("tenant-1", "payment-1"))
                    .isInstanceOf(TooManyRequestsException.class);
            assertThat(dependencyCalls).hasValue(0);
        }
    }
}
