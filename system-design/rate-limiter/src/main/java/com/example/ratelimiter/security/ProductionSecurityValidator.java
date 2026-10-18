package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Guardrail zapobiegający uruchomieniu profilu produkcyjnego z sekretami
 * demonstracyjnymi. Sekrety nadal powinny pochodzić z zewnętrznego secret store.
 */
@Component
@Profile("prod")
public class ProductionSecurityValidator implements SmartInitializingSingleton {
    private final RateLimiterProperties properties;

    public ProductionSecurityValidator(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        var security = properties.getSecurity();
        rejectDefault("admin token", security.getAdminToken(), "local-admin-token");
        rejectDefault("identity HMAC secret", security.getIdentityHmacSecret(), "local-identity-hmac-secret");
        rejectDefault("gateway assertion HMAC secret", security.getGatewayAssertionHmacSecret(),
                "local-gateway-assertion-secret");
    }

    private void rejectDefault(String name, String value, String defaultValue) {
        if (value == null || value.isBlank() || defaultValue.equals(value)) {
            throw new IllegalStateException(name + " must be supplied outside application.yml in prod");
        }
    }
}
