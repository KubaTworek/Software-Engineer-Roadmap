package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Demonstrates Spring Cache abstraction.
 *
 * @Cacheable internally works using AOP proxy.
 */
@Service
public class ProductCacheService {

    /**
     * First call executes method normally.
     * Next calls return cached result.
     */
    @Cacheable("products")
    public String getProduct(Long id) {

        System.out.println(
                "Loading product from database"
        );

        return "Product " + id;
    }
}