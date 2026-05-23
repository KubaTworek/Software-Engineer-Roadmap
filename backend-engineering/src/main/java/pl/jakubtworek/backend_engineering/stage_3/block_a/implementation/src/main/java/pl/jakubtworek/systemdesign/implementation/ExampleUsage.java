package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Demonstrates how the implementation and test concept classes can be composed.
 */
public class ExampleUsage {

    public static void main(String[] args) {
        StatelessApiAssessment stateless = new StatelessApiAssessment(
                "checkout-api",
                List.of(
                        new StatefulRisk(
                                "session",
                                StateLocation.REDIS,
                                true,
                                "Session is externalized to Redis.",
                                "Keep Redis highly available and monitor latency."
                        )
                )
        );

        AutoscalingGuardrails autoscaling = new AutoscalingGuardrails(
                2,
                20,
                ScalingSignal.REQUEST_CONCURRENCY,
                WorkloadType.IO_BOUND,
                10,
                250
        );

        CacheAsideReadPolicy productCache = new CacheAsideReadPolicy(
                "product-details",
                DataFreshnessClass.SHORT_STALE_ACCEPTABLE,
                Duration.ofMinutes(10),
                CacheInvalidationMode.INVALIDATE_ON_WRITE,
                true
        );

        RedisCacheConfiguration redis = new RedisCacheConfiguration(
                8L * 1024 * 1024 * 1024,
                512L * 1024 * 1024,
                1024L * 1024 * 1024,
                RedisEvictionPolicy.ALLKEYS_LRU
        );

        StampedeProtectionPolicy stampede = new StampedeProtectionPolicy(
                Set.of(
                        StampedeProtectionStrategy.SINGLE_FLIGHT,
                        StampedeProtectionStrategy.STALE_WHILE_REVALIDATE,
                        StampedeProtectionStrategy.TTL_JITTER
                ),
                Duration.ofMinutes(2),
                Duration.ofSeconds(30)
        );

        RateLimitLayering rateLimits = new RateLimitLayering(
                new RateLimitPolicy(
                        "edge-ip-limit",
                        RateLimitIdentity.IP_ADDRESS,
                        RateLimitAlgorithm.TOKEN_BUCKET,
                        1000,
                        Duration.ofMinutes(1),
                        true
                ),
                new RateLimitPolicy(
                        "tenant-limit",
                        RateLimitIdentity.TENANT_ID,
                        RateLimitAlgorithm.SLIDING_WINDOW,
                        10000,
                        Duration.ofMinutes(1),
                        true
                )
        );

        RemoteCallPolicy paymentCall = new RemoteCallPolicy(
                "payment-api",
                RemoteCallType.PAYMENT_API,
                Duration.ofMillis(200),
                Duration.ofSeconds(2),
                2,
                Duration.ofMillis(100),
                Duration.ofSeconds(1),
                true,
                true,
                IdempotencyRequirement.IDEMPOTENCY_KEY_REQUIRED
        );

        GracefulDegradationPlan degradation = new GracefulDegradationPlan(
                "checkout",
                Set.of("recommendations", "personalization", "enrichment"),
                Set.of(EmergencyLever.DISABLE_RECOMMENDATIONS, EmergencyLever.REDUCE_DEPENDENCY_CONCURRENCY),
                "Checkout remains available while optional recommendations are hidden."
        );

        QueueProcessingPolicy queue = new QueueProcessingPolicy(
                "order-events",
                true,
                true,
                true,
                Duration.ofMinutes(10),
                3
        );

        ProductionReadinessReview review = new ProductionReadinessReview(
                stateless,
                autoscaling,
                List.of(productCache),
                redis,
                stampede,
                rateLimits,
                List.of(paymentCall),
                degradation,
                List.of(queue),
                LoadTestScenarioCatalog.defaultScenarios()
        );

        review.findings().forEach(System.out::println);
    }
}
