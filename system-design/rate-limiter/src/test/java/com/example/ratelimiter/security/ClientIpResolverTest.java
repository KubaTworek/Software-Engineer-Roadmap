package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void shouldUseFirstUntrustedIpFromForwardedChainWhenRemoteProxyIsTrusted() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getSecurity().setTrustedProxies(List.of("10.0.0.0/8", "192.168.0.0/16"));
        ClientIpResolver resolver = new ClientIpResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 192.168.1.10, 10.1.2.3");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void shouldIgnoreForwardedHeaderWhenRemoteAddressIsNotTrusted() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getSecurity().setTrustedProxies(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.20");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.1.2.3");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.20");
    }
}
