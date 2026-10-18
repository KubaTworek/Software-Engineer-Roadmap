package com.example.ratelimiter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RateLimiterEngine;
import com.example.ratelimiter.core.RuleDecision;
import com.example.ratelimiter.security.ClientIpResolver;
import com.example.ratelimiter.security.RequestIdentityResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    @Test
    void deniedRequestUses429ContractAndDoesNotReachController() throws Exception {
        RateLimiterEngine engine = mock(RateLimiterEngine.class);
        when(engine.check(any())).thenReturn(new RateLimitDecision(false, 10, 0, 7,
                List.of(new RuleDecision("endpoint", false, 10, 0, 7, "redis", "LIMIT"))));
        ClientIpResolver ips = mock(ClientIpResolver.class);
        when(ips.resolve(any())).thenReturn("203.0.113.7");
        RequestIdentityResolver identities = mock(RequestIdentityResolver.class);
        when(identities.resolve(any())).thenReturn(
                new RequestIdentityResolver.Identity(null, "user", "tenant", "FREE"));
        var filter = new RateLimitFilter(engine, ips, identities);
        var request = new MockHttpServletRequest("GET", "/api/users");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("7");
        assertThat(response.getHeader("RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("RateLimit-Reset")).isEqualTo("7");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("rate_limit_exceeded");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowedRequestDoesNotClaimThatBucketResetsImmediately() throws Exception {
        RateLimiterEngine engine = mock(RateLimiterEngine.class);
        when(engine.check(any())).thenReturn(new RateLimitDecision(true, 10, 9, 0,
                List.of(new RuleDecision("endpoint", true, 10, 9, 0, "redis", "OK"))));
        ClientIpResolver ips = mock(ClientIpResolver.class);
        when(ips.resolve(any())).thenReturn("203.0.113.7");
        RequestIdentityResolver identities = mock(RequestIdentityResolver.class);
        when(identities.resolve(any())).thenReturn(
                new RequestIdentityResolver.Identity(null, "user", "tenant", "FREE"));
        var filter = new RateLimitFilter(engine, ips, identities);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/users"), response, chain);

        assertThat(response.getHeader("RateLimit-Reset")).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
