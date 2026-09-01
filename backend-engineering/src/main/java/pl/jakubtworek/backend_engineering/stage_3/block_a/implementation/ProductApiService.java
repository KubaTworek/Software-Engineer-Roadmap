package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.cache.CacheAsideService;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.degradation.ProductPage;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.ratelimit.RateLimitDecision;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.ratelimit.RateLimiter;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.remote.ResilientRemoteClient;

import java.util.Objects;

/**
 * Application-level composition of the canonical mechanisms from {@code concepts}.
 *
 * <p>The read path applies rate limiting before cache-aside; its cache loader may use
 * {@code ProductPageService} to degrade optional recommendations. The payment path
 * applies the same admission control before protecting {@link PaymentGateway} with
 * timeout, retry and circuit breaker policies.</p>
 */
public class ProductApiService {

    private final RateLimiter tenantRateLimiter;
    private final CacheAsideService<String, ProductPage> productPageCache;
    private final ResilientRemoteClient paymentClient;
    private final PaymentGateway paymentGateway;

    public ProductApiService(
            RateLimiter tenantRateLimiter,
            CacheAsideService<String, ProductPage> productPageCache,
            ResilientRemoteClient paymentClient,
            PaymentGateway paymentGateway
    ) {
        this.tenantRateLimiter = Objects.requireNonNull(tenantRateLimiter, "tenantRateLimiter must not be null");
        this.productPageCache = Objects.requireNonNull(productPageCache, "productPageCache must not be null");
        this.paymentClient = Objects.requireNonNull(paymentClient, "paymentClient must not be null");
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway must not be null");
    }

    public ProductPage getProductPage(String tenantId, String productId) {
        RateLimitDecision rateLimit = tenantRateLimiter.allow(tenantId);

        if (!rateLimit.allowed()) {
            throw new TooManyRequestsException(rateLimit.retryAfter().toSeconds());
        }

        return productPageCache.get(productId);
    }

    public String reservePayment(String tenantId, String paymentId) throws Exception {
        RateLimitDecision rateLimit = tenantRateLimiter.allow(tenantId);

        if (!rateLimit.allowed()) {
            throw new TooManyRequestsException(rateLimit.retryAfter().toSeconds());
        }

        return paymentClient.execute(() -> paymentGateway.reserve(paymentId));
    }
}
