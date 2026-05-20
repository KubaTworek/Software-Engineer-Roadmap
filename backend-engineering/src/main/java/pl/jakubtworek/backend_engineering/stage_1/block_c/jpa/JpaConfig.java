package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enables Spring Data JPA repositories.
 *
 * Spring automatically creates repository implementations at runtime.
 */
@Configuration
@EnableJpaRepositories(basePackages = "demo.jpa")
public class JpaConfig {
}