package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot entry point.
 *
 * The application is designed as a stateless backend service. Runtime state
 * should not be stored in memory because Cloud Run or Kubernetes may start,
 * stop, or replace instances at any time.
 */
@SpringBootApplication
public class CloudArchitectureApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudArchitectureApplication.class, args);
    }
}
