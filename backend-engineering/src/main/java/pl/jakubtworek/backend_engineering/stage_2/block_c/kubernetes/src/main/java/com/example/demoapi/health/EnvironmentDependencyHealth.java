package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.health;

import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.DependencyHealth;

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