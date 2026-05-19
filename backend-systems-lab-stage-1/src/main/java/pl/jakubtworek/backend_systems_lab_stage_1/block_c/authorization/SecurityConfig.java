package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Main Spring Security configuration.
 *
 * This application acts as an OAuth2 Resource Server.
 * It expects JWT tokens in the Authorization header:
 *
 * Authorization: Bearer <token>
 */
@Configuration
public class SecurityConfig {

    /**
     * Configures HTTP security.
     *
     * Spring Security will:
     * - read Bearer token from request,
     * - validate JWT signature,
     * - validate token expiration,
     * - create Authentication object,
     * - put Authentication into SecurityContext.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                /**
                 * Public endpoints do not require authentication.
                 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                        .anyRequest().authenticated()
                )

                /**
                 * Enables JWT-based Resource Server support.
                 *
                 * Works with properties:
                 *
                 * spring.security.oauth2.resourceserver.jwt.issuer-uri=...
                 *
                 * or:
                 *
                 * spring.security.oauth2.resourceserver.jwt.jwk-set-uri=...
                 */
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(Customizer.withDefaults())
                )

                .build();
    }
}