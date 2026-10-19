package com.example.ratelimiter.security;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.ratelimiter.config.RateLimiterProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestIdentityResolverTest {
    @Test void publicCallerCannotPromoteItsOwnPlanOrTenant() {
        RateLimiterProperties properties = properties();
        var resolver = new RequestIdentityResolver(properties, new ClientIpResolver(properties), new ApiKeyHasher(properties));
        var request = request("198.51.100.10");
        request.addHeader("X-Tenant-Id", "victim"); request.addHeader("X-Plan", "ENTERPRISE");
        request.addHeader("X-Api-Key", "public-key");

        var identity = resolver.resolve(request);
        assertThat(identity.tenantId()).isNull();
        assertThat(identity.plan()).isEqualTo("FREE");
        assertThat(identity.apiKeyHash()).hasSize(64);
    }

    @Test void trustedGatewayMayAssertAuthenticatedIdentity() {
        RateLimiterProperties properties = properties();
        var resolver = new RequestIdentityResolver(properties, new ClientIpResolver(properties), new ApiKeyHasher(properties));
        var request = request("10.0.0.8");
        request.addHeader("X-Tenant-Id", "tenant-1"); request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-Plan", "PRO");
        var identity = resolver.resolve(request);
        assertThat(identity.tenantId()).isEqualTo("tenant-1");
        assertThat(identity.userId()).isEqualTo("user-1");
        assertThat(identity.plan()).isEqualTo("PRO");
    }

    @Test void trustedNetworkPeerStillNeedsFreshSignatureWhenConfigured() {
        RateLimiterProperties properties = properties();
        properties.getSecurity().setRequireSignedIdentityHeaders(true);
        properties.getSecurity().setIdentitySignatureMaxAgeSeconds(60);
        ApiKeyHasher hmac = new ApiKeyHasher(properties);
        var verifier = new GatewayIdentityVerifier(properties);
        var resolver = new RequestIdentityResolver(properties, new ClientIpResolver(properties), hmac, verifier);
        var request = request("10.0.0.8");
        request.setMethod("GET"); request.setRequestURI("/api/users");
        request.addHeader("X-Tenant-Id", "tenant-1"); request.addHeader("X-User-Id", "user-1");
        request.addHeader("X-Plan", "PRO");

        assertThat(resolver.resolve(request).tenantId()).isNull();

        String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
        request.addHeader("X-Identity-Timestamp", timestamp);
        request.addHeader("X-Identity-Signature", verifier.signatureFor(request, timestamp));
        assertThat(resolver.resolve(request).tenantId()).isEqualTo("tenant-1");
    }

    private RateLimiterProperties properties() {
        var properties = new RateLimiterProperties();
        properties.getSecurity().setTrustedProxies(List.of("10.0.0.0/8"));
        properties.getSecurity().setIdentityHmacSecret("test-secret");
        return properties;
    }

    private MockHttpServletRequest request(String remote) {
        var request = new MockHttpServletRequest(); request.setRemoteAddr(remote); return request;
    }
}
