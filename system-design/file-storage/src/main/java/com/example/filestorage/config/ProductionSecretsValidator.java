package com.example.filestorage.config;

import com.example.filestorage.production.antivirus.AntivirusProperties;
import com.example.filestorage.production.cdn.CdnProperties;
import com.example.filestorage.production.kms.KmsProperties;
import com.example.filestorage.storage.StorageProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Profil produkcyjny dziala fail-closed: wygodne lokalne wartosci domyslne nie
 * moga przez przypadek uruchomic publicznej instancji bez realnego skanera i
 * rotowanych sekretow.
 */
@Component
@Profile("production")
public class ProductionSecretsValidator implements ApplicationRunner {
    private final Environment environment;
    private final StorageProperties storage;
    private final KmsProperties kms;
    private final CdnProperties cdn;
    private final AntivirusProperties antivirus;

    public ProductionSecretsValidator(Environment environment, StorageProperties storage,
                                      KmsProperties kms, CdnProperties cdn,
                                      AntivirusProperties antivirus) {
        this.environment = environment;
        this.storage = storage;
        this.kms = kms;
        this.cdn = cdn;
        this.antivirus = antivirus;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();
        rejectDefault(errors, "JWT_SECRET", environment.getProperty("app.jwt.secret"), "change-this-secret");
        rejectDefault(errors, "MINIO_ACCESS_KEY", storage.accessKey(), "minioadmin");
        rejectDefault(errors, "MINIO_SECRET_KEY", storage.secretKey(), "minioadmin");
        rejectDefault(errors, "LOCAL_KMS_MASTER_KEY", kms.masterKey(), "change-this-local");
        if (!antivirus.enabled() || "NOOP".equalsIgnoreCase(antivirus.mode())) {
            errors.add("production requires a fail-closed antivirus scanner");
        }
        if (cdn.enabled() && (cdn.signingSecret() == null || cdn.signingSecret().startsWith("change-this"))) {
            errors.add("CDN_SIGNING_SECRET must be replaced when CDN is enabled");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", errors));
        }
    }

    private void rejectDefault(List<String> errors, String name, String actual, String forbiddenPrefix) {
        if (actual == null || actual.isBlank() || actual.startsWith(forbiddenPrefix)) {
            errors.add(name + " uses a development default");
        }
    }
}
