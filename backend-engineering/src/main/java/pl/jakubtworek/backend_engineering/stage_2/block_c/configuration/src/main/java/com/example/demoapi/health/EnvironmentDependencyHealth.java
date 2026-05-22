package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.health;

import org.springframework.stereotype.Component;

@Component
public class EnvironmentDependencyHealth implements DependencyHealth {

    @Override
    public boolean isHealthy() {
        // This is a simple simulation of an external dependency.
        // In a real service, dependency status should often be refreshed in the background
        // and cached in memory, instead of calling the dependency synchronously on every probe.
        String value = System.getenv("DB_UP");
        return !"false".equalsIgnoreCase(value);
    }
}