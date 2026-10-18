package com.example.videostreaming.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fail-closed guard przeciw uruchomieniu profilu prod z sekretami demonstracyjnymi. */
@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {
    private final String jwtSecret;
    private final String playbackSecret;
    private final String storageAccessKey;
    private final boolean secureCookie;
    private final boolean demoAdminEnabled;

    public ProductionConfigurationValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.premium.signed-cookie-secret}") String playbackSecret,
            @Value("${app.storage.access-key}") String storageAccessKey,
            @Value("${app.premium.cookie-secure}") boolean secureCookie,
            @Value("${app.demo-admin.enabled:false}") boolean demoAdminEnabled) {
        this.jwtSecret = jwtSecret;
        this.playbackSecret = playbackSecret;
        this.storageAccessKey = storageAccessKey;
        this.secureCookie = secureCookie;
        this.demoAdminEnabled = demoAdminEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jwtSecret.contains("dev-secret") || playbackSecret.contains("dev-signed")
                || "minioadmin".equals(storageAccessKey) || !secureCookie) {
            throw new IllegalStateException("Production profile rejects demo secrets and insecure playback cookies");
        }
        if (demoAdminEnabled) {
            throw new IllegalStateException("Production profile rejects the local demonstration administrator");
        }
    }
}
