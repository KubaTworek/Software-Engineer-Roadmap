package pl.jakubtworek.cloudarchitecture;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Main Spring Boot entry point.
 *
 * The application is designed as a stateless backend service. Runtime state
 * should not be stored in memory because Cloud Run or Kubernetes may start,
 * stop, or replace instances at any time.
 */
@SpringBootApplication
@EnableScheduling
public class CloudArchitectureApplication {
    /** Shared UTC clock keeps time-dependent domain code explicit and testable. */
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        SpringApplication.run(CloudArchitectureApplication.class, args);
    }
}
