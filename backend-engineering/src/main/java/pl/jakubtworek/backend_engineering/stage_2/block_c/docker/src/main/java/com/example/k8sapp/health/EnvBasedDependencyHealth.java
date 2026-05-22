package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health;

import org.springframework.stereotype.Component;

@Component
public class EnvBasedDependencyHealth implements DependencyHealth {

    @Override
    public boolean isHealthy() {
        // This intentionally simulates dependency health with an environment variable.
        // In a real application, dependency status should usually be refreshed in the background
        // and exposed from memory, rather than checked synchronously on every readiness request.
        String value = System.getenv("DB_UP");
        return !"false".equalsIgnoreCase(value);
    }
}