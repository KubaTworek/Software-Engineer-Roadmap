package pl.jakubtworek.backend.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dowodzi dwóch odmiennych gwarancji gatewaya: przeciążenie jest odrzucane przed
 * downstreamem, natomiast utrata pomocniczego Redisa nie odcina ruchu biznesowego.
 */
class RateLimitFilterTest {

    private final RateLimitProperties properties = new RateLimitProperties(true,
            new RateLimitProperties.Bucket(10, 10, Duration.ofMinutes(1)),
            new RateLimitProperties.Bucket(100, 100, Duration.ofMinutes(1)));
    private final RedisTokenBucketRateLimiter limiter = mock(RedisTokenBucketRateLimiter.class);
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void rejectsExhaustedBucketBeforeCallingDownstream() throws Exception {
        when(limiter.consume(anyString(), any())).thenReturn(new TokenBucketDecision(false, 0, 7));
        var request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-API-Key", "partner-1");
        var response = new MockHttpServletResponse();

        new RateLimitFilter(properties, limiter).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("7");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void failsOpenWhenRedisIsUnavailableAndMarksDegradation() throws Exception {
        when(limiter.consume(anyString(), any()))
                .thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        var request = new MockHttpServletRequest("GET", "/api/events");
        var response = new MockHttpServletResponse();

        new RateLimitFilter(properties, limiter).doFilterInternal(request, response, chain);

        assertThat(response.getHeader("X-RateLimit-Degraded")).isEqualTo("true");
        verify(chain).doFilter(request, response);
    }
}
