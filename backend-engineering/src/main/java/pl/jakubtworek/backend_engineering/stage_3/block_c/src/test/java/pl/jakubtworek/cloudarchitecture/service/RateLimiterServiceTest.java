package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import pl.jakubtworek.cloudarchitecture.service.RateLimitExceededException;
import pl.jakubtworek.cloudarchitecture.service.RateLimiterService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterServiceTest {

    @Test
    void incrementsAndSetsExpiryThroughOneAtomicRedisScript() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("rate-limit:user-1")), eq("60")))
                .thenReturn(100L);

        new RateLimiterService(redis).checkLimit("user-1");

        verify(redis).execute(any(RedisScript.class), eq(List.of("rate-limit:user-1")), eq("60"));
        verify(redis, never()).expire(any(String.class), any(java.time.Duration.class));
    }

    @Test
    void rejectsTheFirstRequestAboveTheLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), any(List.class), eq("60"))).thenReturn(101L);

        assertThatThrownBy(() -> new RateLimiterService(redis).checkLimit("user-1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("user-1");
    }

    @Test
    void rejectsBlankUserBeforeCallingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateLimiterService(redis).checkLimit(" "));

        verify(redis, never()).execute(any(RedisScript.class), any(List.class), any());
    }
}
