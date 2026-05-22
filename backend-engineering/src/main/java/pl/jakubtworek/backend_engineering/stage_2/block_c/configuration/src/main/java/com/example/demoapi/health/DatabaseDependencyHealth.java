package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.health;

import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config.SecretProperties;

@Component
public class DatabaseDependencyHealth implements DependencyHealth {

    private final SecretProperties secretProperties;

    public DatabaseDependencyHealth(SecretProperties secretProperties) {
        this.secretProperties = secretProperties;
    }

    @Override
    public boolean isHealthy() {
        // This is a lightweight simulation.
        // In a real system, dependency health should usually be refreshed in the background
        // and cached, instead of performing a database call on every readiness probe.
        String forcedState = System.getenv("DB_UP");

        if ("false".equalsIgnoreCase(forcedState)) {
            return false;
        }

        return secretProperties.hasDatabaseDsn();
    }
}