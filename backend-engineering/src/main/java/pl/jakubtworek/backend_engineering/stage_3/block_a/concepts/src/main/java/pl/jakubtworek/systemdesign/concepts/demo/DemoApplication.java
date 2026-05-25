package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.demo;

import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.cache.*;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.capacity.*;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.degradation.*;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.ratelimit.*;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.resilience.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Example request path:
 * - rate limiting per tenant
 * - cache-aside for product page
 * - graceful degradation for recommendations
 * - capacity analysis for bottleneck prediction
 */
public class DemoApplication {

    public static void main(String[] args) throws Exception {
        BottleneckAnalyzer analyzer = new BottleneckAnalyzer();

        CapacityPlan capacityPlan = new CapacityPlan(
                4,
                2.0,
                0.70,
                0.003,
                30,
                0.10,
                0.4,
                1200,
                0.20,
                2
        );

        Bottleneck first = analyzer.first(capacityPlan);

        System.out.println("First bottleneck: " + first);
        System.out.println("Payment concurrency at 750 RPS: "
                + CapacityCalculator.concurrency(750 * 0.10, 0.4));

        EmergencyLeverRegistry levers = new EmergencyLeverRegistry();

        RecommendationClient recommendationClient = productId ->
                List.of("similar-product-1", "similar-product-2");

        ProductPageService productPageService = new ProductPageService(
                levers,
                recommendationClient
        );

        CacheClient<String, ProductPage> cache = new InMemoryTtlCache<>();

        CacheAsideService<String, ProductPage> cacheAside = new CacheAsideService<>(
                cache,
                productPageService::getProductPage,
                Duration.ofMinutes(5),
                new TtlJitter(Duration.ofSeconds(30))
        );

        RateLimiter tenantLimiter = new TokenBucketRateLimiter(
                100,
                20
        );

        RateLimitDecision decision = tenantLimiter.allow("tenant-123");

        if (!decision.isAllowed().allowed()) {
            System.out.println("429 Too Many Requests. Retry after: " + decision.retryAfter());
            return;
        }

        ProductPage page = cacheAside.get("product-1");
        System.out.println(page);

        levers.enable(EmergencyLever.DISABLE_RECOMMENDATIONS);

        cacheAside.invalidate("product-1");
        ProductPage degradedPage = cacheAside.get("product-1");

        System.out.println(degradedPage);

        try (TimeoutExecutor timeoutExecutor = new TimeoutExecutor(Executors.newFixedThreadPool(4))) {
            ResilientRemoteClient paymentClient = new ResilientRemoteClient(
                    new CircuitBreaker("payment-api", 3, Duration.ofSeconds(10)),
                    new RetryExecutor(
                            3,
                            Duration.ofMillis(100),
                            Duration.ofSeconds(1),
                            exception -> exception instanceof RemoteTimeoutException
                    ),
                    timeoutExecutor,
                    new TimeoutConfig(
                            Duration.ofMillis(200),
                            Duration.ofSeconds(2)
                    )
            );

            String result = paymentClient.execute(() -> "payment-reserved");
            System.out.println(result);
        }
    }
}