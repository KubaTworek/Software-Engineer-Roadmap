package com.example.ratelimiter.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.ratelimiter.config.RateLimiterProperties;
import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {
    @Test
    void productionMustNotStartWithEducationalDefaultSecrets() {
        assertThatThrownBy(() -> new ProductionSecurityValidator(new RateLimiterProperties())
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin token");
    }

    @Test
    void externallySuppliedSecretsPassTheStartupGuardrail() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getSecurity().setAdminToken("secret-from-vault");
        properties.getSecurity().setIdentityHmacSecret("rotatable-hmac-secret-from-vault");
        properties.getSecurity().setGatewayAssertionHmacSecret("gateway-secret-from-vault");

        assertThatCode(() -> new ProductionSecurityValidator(properties).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }
}
