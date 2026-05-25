package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis/Memorystore cache abstraction for products.
 *
 * Keeping cache logic in a dedicated class avoids spreading Redis keys and TTL
 * rules across the entire codebase.
 */
@Service
public class ProductCacheService {
    private static final Duration PRODUCT_TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads a product from cache.
     *
     * Cache failures should normally not break the main request path.
     */
    public Optional<ProductDto> get(Long productId) {
        try {
            String value = redisTemplate.opsForValue().get(key(productId));
            if (value == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(value, ProductDto.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** Stores product data in cache with a short TTL. */
    public void put(ProductDto product) {
        try {
            redisTemplate.opsForValue().set(key(product.id()), objectMapper.writeValueAsString(product), PRODUCT_TTL);
        } catch (Exception ignored) {
            // Cache write failure should not usually break the main request path.
        }
    }

    /** Removes a product from cache after a write operation. */
    public void evict(Long productId) {
        redisTemplate.delete(key(productId));
    }

    private String key(Long productId) {
        return "product:" + productId;
    }
}
