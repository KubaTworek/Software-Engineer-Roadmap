package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.cache.CacheAsideService;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.degradation.ProductPage;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.degradation.ProductPageService;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.ratelimit.RateLimitResult;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.ratelimit.RateLimiter;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.remote.ResilientRemoteClient;

/**
 * Example application service showing how the implementation pieces fit together.
 *
 * The request path includes:
 * - rate limiting
 * - cache-aside
 * - resilient remote call
 * - graceful degradation
 */
public class ProductApiService {

    private final RateLimiter tenantRateLimiter;
    private final CacheAsideService<String, ProductPage> productPageCache;
    private final ResilientRemoteClient paymentClient;
    private final ProductPageService productPageService;

    public ProductApiService(
            RateLimiter tenantRateLimiter,
            CacheAsideService<String, ProductPage> productPageCache,
            ResilientRemoteClient paymentClient,
            ProductPageService productPageService
    ) {
        this.tenantRateLimiter = tenantRateLimiter;
        this.productPageCache = productPageCache;
        this.paymentClient = paymentClient;
        this.productPageService = productPageService;
    }

    public ProductPage getProductPage(String tenantId, String productId) throws Exception {
        RateLimitResult rateLimit = tenantRateLimiter.allow(tenantId);

        if (!rateLimit.allowed()) {
            throw new TooManyRequestsException(rateLimit.retryAfter().toSeconds());
        }

        return productPageCache.get(productId);
    }

    public void reservePayment(String tenantId, String paymentId) throws Exception {
        RateLimitResult rateLimit = tenantRateLimiter.allow(tenantId);

        if (!rateLimit.allowed()) {
            throw new TooManyRequestsException(rateLimit.retryAfter().toSeconds());
        }

        paymentClient.execute(() -> {
            // Real implementation would call payment API here.
            return "reserved:" + paymentId;
        });
    }
}