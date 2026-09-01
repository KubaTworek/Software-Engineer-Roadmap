package pl.jakubtworek.cloudarchitecture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import pl.jakubtworek.cloudarchitecture.dto.OrderCreatedResponse;
import pl.jakubtworek.cloudarchitecture.service.IdempotencyService;
import pl.jakubtworek.cloudarchitecture.service.IdempotencyConflictException;
import pl.jakubtworek.cloudarchitecture.service.IdempotencyInProgressException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked") // Mockito creates generic Redis test doubles through type erasure.
class IdempotencyServiceTest {

    @Test
    void returnsCachedResponseWithoutRepeatingTheOperation() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectMapper mapper = new ObjectMapper();
        OrderCreatedResponse cached = new OrderCreatedResponse(42L, "ACCEPTED");
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("idem:request-1", "PROCESSING|hash-1", Duration.ofMinutes(2)))
                .thenReturn(false);
        when(values.get("idem:request-1"))
                .thenReturn("COMPLETED|hash-1|" + mapper.writeValueAsString(cached));
        AtomicInteger executions = new AtomicInteger();

        OrderCreatedResponse result = new IdempotencyService(redis, mapper).executeOnce(
                "request-1",
                "hash-1",
                OrderCreatedResponse.class,
                () -> {
                    executions.incrementAndGet();
                    return new OrderCreatedResponse(99L, "ACCEPTED");
                }
        );

        assertThat(result).isEqualTo(cached);
        assertThat(executions).hasValue(0);
        verify(values, never()).set(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void storesACompletedOperationForLaterRetries() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("idem:request-2", "PROCESSING|hash-2", Duration.ofMinutes(2)))
                .thenReturn(true);
        when(redis.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("idem:request-2")),
                eq("PROCESSING|hash-2"),
                anyString(),
                eq(Long.toString(Duration.ofHours(1).toMillis()))
        )).thenReturn(1L);
        OrderCreatedResponse created = new OrderCreatedResponse(7L, "ACCEPTED");

        OrderCreatedResponse result = new IdempotencyService(redis, new ObjectMapper()).executeOnce(
                "request-2",
                "hash-2",
                OrderCreatedResponse.class,
                () -> created
        );

        assertThat(result).isEqualTo(created);
        verify(redis).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("idem:request-2")),
                eq("PROCESSING|hash-2"),
                org.mockito.ArgumentMatchers.contains("COMPLETED|hash-2|"),
                eq(Long.toString(Duration.ofHours(1).toMillis()))
        );
    }

    @Test
    void preservesTheOriginalBusinessExceptionAndDoesNotCacheFailure() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("idem:request-3", "PROCESSING|hash-3", Duration.ofMinutes(2)))
                .thenReturn(true);
        RuntimeException failure = new RuntimeException("payment rejected");

        IdempotencyService service = new IdempotencyService(redis, new ObjectMapper());

        assertThatThrownBy(() -> service.executeOnce(
                "request-3",
                "hash-3",
                OrderCreatedResponse.class,
                () -> {
                    throw failure;
                }
        )).isSameAs(failure);
        verify(values, never()).set(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void rejectsBlankKeysBeforeCallingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        assertThatIllegalArgumentException().isThrownBy(() ->
                new IdempotencyService(redis, new ObjectMapper()).executeOnce(
                        " ",
                        "hash-4",
                        OrderCreatedResponse.class,
                        () -> new OrderCreatedResponse(1L, "ACCEPTED")
                )
        );

        verify(redis, never()).opsForValue();
    }

    @Test
    void rejectsAConcurrentRequestUsingTheSamePayload() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("idem:request-5", "PROCESSING|hash-5", Duration.ofMinutes(2)))
                .thenReturn(false);
        when(values.get("idem:request-5")).thenReturn("PROCESSING|hash-5");

        assertThatThrownBy(() -> new IdempotencyService(redis, new ObjectMapper()).executeOnce(
                "request-5",
                "hash-5",
                OrderCreatedResponse.class,
                () -> new OrderCreatedResponse(5L, "ACCEPTED")
        )).isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void rejectsReusingAKeyForAnotherPayload() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("idem:request-6", "PROCESSING|new-hash", Duration.ofMinutes(2)))
                .thenReturn(false);
        when(values.get("idem:request-6")).thenReturn("COMPLETED|old-hash|{}");

        assertThatThrownBy(() -> new IdempotencyService(redis, new ObjectMapper()).executeOnce(
                "request-6",
                "new-hash",
                OrderCreatedResponse.class,
                () -> new OrderCreatedResponse(6L, "ACCEPTED")
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void doesNotOverwriteANewerOwnerWhenTheProcessingLeaseExpired() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("idem:request-7", "PROCESSING|hash-7", Duration.ofMinutes(2)))
                .thenReturn(true);
        when(redis.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("idem:request-7")),
                eq("PROCESSING|hash-7"),
                anyString(),
                eq(Long.toString(Duration.ofHours(1).toMillis()))
        )).thenReturn(0L);
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> new IdempotencyService(redis, new ObjectMapper()).executeOnce(
                "request-7",
                "hash-7",
                OrderCreatedResponse.class,
                () -> {
                    executions.incrementAndGet();
                    return new OrderCreatedResponse(7L, "ACCEPTED");
                }
        )).isInstanceOf(IdempotencyInProgressException.class)
                .hasMessageContaining("reconciliation");

        assertThat(executions).hasValue(1);
    }
}
