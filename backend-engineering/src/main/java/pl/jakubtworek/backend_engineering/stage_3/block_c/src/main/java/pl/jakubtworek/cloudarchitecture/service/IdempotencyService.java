package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Implements idempotent execution using Redis/Memorystore.
 *
 * This protects write endpoints against duplicate effects caused by retries.
 */
@Service
public class IdempotencyService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(1);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(2);
    private static final String PROCESSING = "PROCESSING|";
    private static final String COMPLETED = "COMPLETED|";
    private static final DefaultRedisScript<Long> DELETE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]); end; return 0;",
            Long.class
    );
    private static final DefaultRedisScript<Long> COMPLETE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]); return 1; "
                    + "end; return 0;",
            Long.class
    );
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Executes an operation once for a given idempotency key.
     *
     * If the same key and request fingerprint are used again, the previously
     * stored response is returned. Claim completion is compare-and-set: a slow
     * owner cannot overwrite a newer owner after its processing lease expires.
     *
     * Redis cannot atomically commit an arbitrary external business effect. The
     * operation must therefore also be naturally idempotent, use the same durable
     * transaction as its idempotency record, or pass an idempotency key further
     * downstream.
     */
    public <T> T executeOnce(
            String idempotencyKey,
            String requestFingerprint,
            Class<T> responseType,
            Supplier<T> operation
    ) {
        String key = key(requireNonBlank(idempotencyKey, "idempotencyKey"));
        String fingerprint = requireNonBlank(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(responseType, "responseType must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        String processingValue = PROCESSING + fingerprint;

        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, processingValue, PROCESSING_TTL);
        if (!Boolean.TRUE.equals(claimed)) {
            return readExisting(key, fingerprint, responseType);
        }

        T result;
        try {
            result = operation.get();
        } catch (RuntimeException | Error exception) {
            try {
                releaseClaim(key, processingValue);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }

        String completedValue = COMPLETED + fingerprint + "|" + serializeResponse(result);
        Long completed = redisTemplate.execute(
                COMPLETE_IF_OWNER,
                java.util.List.of(key),
                processingValue,
                completedValue,
                Long.toString(IDEMPOTENCY_TTL.toMillis())
        );
        if (!Long.valueOf(1L).equals(completed)) {
            throw new IdempotencyInProgressException(
                    "idempotency claim expired before completion; operation outcome requires reconciliation"
            );
        }
        return result;
    }

    /**
     * Hashes the serialized request. Production code should additionally define
     * canonical representation rules for maps, numbers and semantically equal
     * payload variants before treating this as a business fingerprint.
     */
    public String fingerprint(Object request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            byte[] serializedRequest = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serializedRequest));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("request cannot be serialized", ex);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private <T> T readExisting(String key, String fingerprint, Class<T> responseType) {
        String state = redisTemplate.opsForValue().get(key);
        if (state == null) {
            throw new IdempotencyInProgressException("idempotency state changed; retry the request");
        }
        if (state.equals(PROCESSING + fingerprint)) {
            throw new IdempotencyInProgressException("request with this idempotency key is still processing");
        }
        String completedPrefix = COMPLETED + fingerprint + "|";
        if (state.startsWith(completedPrefix)) {
            try {
                return objectMapper.readValue(state.substring(completedPrefix.length()), responseType);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("stored idempotency response is invalid", exception);
            }
        }
        if (state.startsWith(PROCESSING) || state.startsWith(COMPLETED)) {
            throw new IdempotencyConflictException("idempotency key was already used for another request");
        }
        throw new IllegalStateException("unknown idempotency state");
    }

    private String serializeResponse(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            // Do not release the claim: the business operation already succeeded.
            throw new IllegalStateException("could not serialize idempotent response", exception);
        }
    }

    private void releaseClaim(String key, String processingValue) {
        redisTemplate.execute(DELETE_IF_OWNER, java.util.List.of(key), processingValue);
    }

    private String key(String idempotencyKey) {
        return "idem:" + idempotencyKey;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
