package com.example.paymentsystem.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

/**
 * Produkcyjna granica przykładu: operacje zmieniające pieniądze nie mogą być
 * anonimowymi endpointami. Basic Auth jest tu wyłącznie małym, wykonywalnym
 * modelem RBAC; prawdziwe wdrożenie powinno używać OIDC, MFA dla operatorów
 * oraz krótkotrwałych poświadczeń workload identity.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain paymentSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/v1/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/v1/webhooks/**").permitAll()
                        .requestMatchers("/v1/merchants/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/v1/approvals/**").hasRole("OPERATOR")
                        .requestMatchers("/v1/admin/**", "/v1/payments/*/succeed").hasRole("ADMIN")
                        .requestMatchers("/v1/payments/*/refunds", "/v1/payments/*/capture",
                                "/v1/payments/*/cancel", "/v1/merchants/*/payouts",
                                "/v1/payments/*/chargebacks", "/v1/chargebacks/**").hasRole("OPERATOR")
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${payment-system.security.allowed-origin:http://localhost:3000}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                "X-Webhook-Timestamp", "X-Webhook-Signature"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService educationalOperators(
            PasswordEncoder encoder,
            @Value("${payment-system.security.operator-password:change-me-locally}") String operatorPassword,
            @Value("${payment-system.security.admin-password:change-me-locally}") String adminPassword
    ) {
        return new InMemoryUserDetailsManager(
                User.withUsername("operator").password(encoder.encode(operatorPassword)).roles("OPERATOR").build(),
                User.withUsername("admin").password(encoder.encode(adminPassword)).roles("ADMIN", "OPERATOR").build()
        );
    }
}
