package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Headers such as tenant and plan are assertions, not authentication. They are
 * accepted only from a trusted gateway; a public caller may identify itself by
 * an API key, but cannot promote its own plan or impersonate another tenant.
 */
@Component
public class RequestIdentityResolver {
    private final RateLimiterProperties properties;
    private final ClientIpResolver clients;
    private final ApiKeyHasher apiKeys;
    private final GatewayIdentityVerifier gatewayIdentity;

    @Autowired
    public RequestIdentityResolver(RateLimiterProperties properties, ClientIpResolver clients,
            ApiKeyHasher apiKeys, GatewayIdentityVerifier gatewayIdentity) {
        this.properties = properties;
        this.clients = clients;
        this.apiKeys = apiKeys;
        this.gatewayIdentity = gatewayIdentity;
    }

    /** Convenience constructor retained for focused unit tests. */
    public RequestIdentityResolver(RateLimiterProperties properties, ClientIpResolver clients, ApiKeyHasher apiKeys) {
        this(properties, clients, apiKeys, new GatewayIdentityVerifier(properties));
    }

    public Identity resolve(HttpServletRequest request) {
        var security = properties.getSecurity();
        boolean acceptAssertions = !security.isIdentityHeadersFromTrustedProxiesOnly()
                || clients.isTrustedPeer(request);
        acceptAssertions = acceptAssertions && gatewayIdentity.isValid(request);
        return new Identity(
                apiKeys.hmacSha256(request.getHeader(security.getApiKeyHeader())),
                acceptAssertions ? request.getHeader(security.getUserHeader()) : null,
                acceptAssertions ? request.getHeader(security.getTenantHeader()) : null,
                acceptAssertions ? defaultPlan(request.getHeader(security.getPlanHeader())) : "FREE");
    }

    private String defaultPlan(String plan) {
        return plan == null || plan.isBlank() ? "FREE" : plan;
    }

    public record Identity(String apiKeyHash, String userId, String tenantId, String plan) {}
}
