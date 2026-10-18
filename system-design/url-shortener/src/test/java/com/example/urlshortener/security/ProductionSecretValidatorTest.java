package com.example.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.admin.AdminProperties;
import com.example.urlshortener.analytics.AnalyticsProperties;
import com.example.urlshortener.edge.EdgeProperties;
import com.example.urlshortener.enterprise.EnterpriseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecretValidatorTest {
    @Test
    void productionProfileRejectsRepositoryDemoSecrets() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        var validator = new ProductionSecretValidator(
                environment,
                new AdminProperties("local-admin-token"),
                new EdgeProperties(),
                new EnterpriseProperties(),
                new AnalyticsProperties(true, "change-me-in-production"));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production admin token");
    }
}
