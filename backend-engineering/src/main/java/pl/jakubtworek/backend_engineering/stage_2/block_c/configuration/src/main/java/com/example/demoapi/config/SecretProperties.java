package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "secret")
public class SecretProperties {

    /**
     * Database DSN injected from Kubernetes Secret.
     * Never log this value in production.
     */
    private String dbDsn = "";

    public String getDbDsn() {
        return dbDsn;
    }

    public void setDbDsn(String dbDsn) {
        this.dbDsn = dbDsn;
    }

    public boolean hasDatabaseDsn() {
        return dbDsn != null && !dbDsn.isBlank();
    }

    public String maskedDatabaseDsn() {
        // This method intentionally avoids exposing the secret value.
        if (!hasDatabaseDsn()) {
            return "<missing>";
        }

        return "<configured>";
    }
}