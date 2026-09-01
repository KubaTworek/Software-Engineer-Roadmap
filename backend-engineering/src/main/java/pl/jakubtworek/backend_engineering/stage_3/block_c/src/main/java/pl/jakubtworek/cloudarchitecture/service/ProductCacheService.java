package pl.jakubtworek.cloudarchitecture.service;

import pl.jakubtworek.cloudarchitecture.dto.ProductDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis/Memorystore cache abstraction for products.
 *
 * Keeping cache logic in a dedicated class avoids spreading Redis keys and TTL
 * rules across the entire codebase.
 */
@Service
public class ProductCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductCacheService.class);
    private static final Duration PRODUCT_TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Reads a product from cache.
     *
     * Cache failures do not break this read path because the database remains
     * the source of truth. They are still observable; silently swallowing them
     * would hide a cache outage and the resulting pressure on the database.
     */
    public Optional<ProductDto> get(Long productId) {
        String cacheKey = key(requirePositive(productId, "productId"));
        String value;
        try {
            value = redisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException exception) {
            logFailure("read", cacheKey, exception);
            return Optional.empty();
        }
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, ProductDto.class));
        } catch (JsonProcessingException exception) {
            logFailure("decode", cacheKey, exception);
            evict(productId);
            return Optional.empty();
        }
    }

    /** Stores product data in cache with a short TTL. */
    public void put(ProductDto product) {
        Objects.requireNonNull(product, "product must not be null");
        Long productId = requirePositive(product.id(), "product.id");
        String value;
        try {
            value = objectMapper.writeValueAsString(product);
        } catch (JsonProcessingException exception) {
            logFailure("encode", key(productId), exception);
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(productId),
                    value,
                    PRODUCT_TTL
            );
        } catch (RuntimeException exception) {
            logFailure("write", key(productId), exception);
        }
    }

    /** Removes a product from cache after a write operation. */
    public void evict(Long productId) {
        Long validatedProductId = requirePositive(productId, "productId");
        try {
            redisTemplate.delete(key(validatedProductId));
        } catch (RuntimeException exception) {
            logFailure("evict", key(validatedProductId), exception);
        }
    }

    private String key(Long productId) {
        return "product:" + productId;
    }

    private static void logFailure(String operation, String cacheKey, Exception exception) {
        // Production systems should additionally expose a bounded-cardinality metric
        // and apply log sampling/rate limiting during a prolonged cache outage.
        LOGGER.warn(
                "product cache operation failed operation={} key={} errorType={}",
                operation,
                cacheKey,
                exception.getClass().getSimpleName()
        );
    }

    private static Long requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }
}
