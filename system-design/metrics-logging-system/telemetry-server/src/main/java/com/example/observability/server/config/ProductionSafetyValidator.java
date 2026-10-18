package com.example.observability.server.config;

import com.example.observability.server.auth.ApiKeyProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Produkcja ma odmówić startu zamiast działać z laboratoryjnymi sekretami. */
@Component
@Profile("production")
public class ProductionSafetyValidator {
    private final ApiKeyProperties keys;
    private final String metadataPassword;

    public ProductionSafetyValidator(ApiKeyProperties keys,
            @Value("${telemetry.metadata.password}") String metadataPassword) {
        this.keys = keys;
        this.metadataPassword = metadataPassword;
    }

    @PostConstruct
    void validate() {
        boolean demoKey = keys.getApiKeys().stream().anyMatch(key ->
                key.getToken() == null || key.getToken().startsWith("demo-"));
        if (!keys.isEnabled() || demoKey || "telemetry-local".equals(metadataPassword)) {
            throw new IllegalStateException("production profile rejects disabled auth and demo secrets");
        }
    }
}
