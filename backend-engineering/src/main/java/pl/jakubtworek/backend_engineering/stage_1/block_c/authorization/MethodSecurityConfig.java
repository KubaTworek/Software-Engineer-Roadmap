package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables method-level security.
 *
 * Required for annotations like:
 * - @PreAuthorize
 * - @PostAuthorize
 * - @Secured
 *
 * Without this annotation, @PreAuthorize will be ignored.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}