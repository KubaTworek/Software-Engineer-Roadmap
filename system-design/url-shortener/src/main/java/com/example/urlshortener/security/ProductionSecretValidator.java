package com.example.urlshortener.security;

import com.example.urlshortener.admin.AdminProperties;
import com.example.urlshortener.analytics.AnalyticsProperties;
import com.example.urlshortener.edge.EdgeProperties;
import com.example.urlshortener.enterprise.EnterpriseProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fails closed when a production process starts with repository demo secrets. */
@Component
public class ProductionSecretValidator implements ApplicationRunner {
    private final Environment environment;
    private final AdminProperties admin;
    private final EdgeProperties edge;
    private final EnterpriseProperties enterprise;
    private final AnalyticsProperties analytics;

    public ProductionSecretValidator(
            Environment environment,
            AdminProperties admin,
            EdgeProperties edge,
            EnterpriseProperties enterprise,
            AnalyticsProperties analytics
    ) {
        this.environment = environment;
        this.admin = admin;
        this.edge = edge;
        this.enterprise = enterprise;
        this.analytics = analytics;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        reject("admin token", admin.token(), "local-admin-token");
        reject("edge token", edge.getInternalToken(), "local-edge-token");
        reject("enterprise HMAC key", enterprise.getApiKeyHashSalt(), "change-me-enterprise-salt");
        reject("analytics HMAC key", analytics.ipHashSalt(), "change-me-in-production");
    }

    private void reject(String name, String value, String knownDefault) {
        if (value == null || value.isBlank() || value.equals(knownDefault)) {
            throw new IllegalStateException("Production " + name + " must be supplied by secret management");
        }
    }
}
