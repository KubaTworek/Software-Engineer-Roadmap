package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Implements idempotent execution using Redis/Memorystore.
 *
 * This protects write endpoints against duplicate effects caused by retries.
 */
@Service
public class IdempotencyService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(1);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes an operation once for a given idempotency key.
     *
     * If the same key is used again, the previously stored response is returned
     * instead of executing the operation again.
     */
    public <T> T executeOnce(String idempotencyKey, Class<T> responseType, Supplier<T> operation) {
        try {
            String key = key(idempotencyKey);
            String cachedResponse = redisTemplate.opsForValue().get(key);
            if (cachedResponse != null) {
                return objectMapper.readValue(cachedResponse, responseType);
            }
            T result = operation.get();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), IDEMPOTENCY_TTL);
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Idempotent operation failed", ex);
        }
    }

    private String key(String idempotencyKey) {
        return "idem:" + idempotencyKey;
    }
}
