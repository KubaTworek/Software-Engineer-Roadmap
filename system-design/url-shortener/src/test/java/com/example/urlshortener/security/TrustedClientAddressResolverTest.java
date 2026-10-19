package com.example.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedClientAddressResolverTest {

    @Test
    void ignoresSpoofedHeaderFromAnUntrustedPeer() {
        var resolver = resolver(List.of("10.0.0.0/8"));
        var request = request("198.51.100.7", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void walksTrustedProxyChainFromTheNearestHop() {
        var resolver = resolver(List.of("10.0.0.0/8"));
        var request = request("10.0.0.3", "203.0.113.9, 10.0.0.1, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void stopsAtAnUntrustedIntermediateHopInsteadOfTrustingTheLeftmostValue() {
        var resolver = resolver(List.of("10.0.0.0/8"));
        var request = request("10.0.0.3", "192.0.2.99, 198.51.100.8, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.8");
    }

    private TrustedClientAddressResolver resolver(List<String> cidrs) {
        var properties = new TrustedProxyProperties();
        properties.setCidrs(cidrs);
        return new TrustedClientAddressResolver(properties);
    }

    private HttpServletRequest request(String remote, String forwarded) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remote);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwarded);
        return request;
    }
}
