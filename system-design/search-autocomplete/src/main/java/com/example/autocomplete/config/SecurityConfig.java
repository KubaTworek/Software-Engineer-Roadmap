package com.example.autocomplete.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Publiczny serving path i operatorski control plane mają różne granice zaufania.
 * HTTP Basic jest tu wyłącznie wykonywalnym modelem RBAC; produkcyjnie operatora
 * uwierzytelniałby gateway/IdP, a sekret nie miałby wartości domyślnej.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // API jest bezstanowe i używa Authorization header, a nie cookie sesyjnego.
                // Przy uwierzytelnianiu przeglądarkowym CSRF trzeba ponownie włączyć.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/api/v1/autocomplete", "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/feedback/**").permitAll()
                        .anyRequest().hasRole("OPERATOR"))
                .httpBasic(basic -> {})
                .build();
    }

    @Bean
    UserDetailsService operator(
            @Value("${app.security.operator.username:operator}") String username,
            @Value("${app.security.operator.password:{noop}change-me}") String password
    ) {
        return new InMemoryUserDetailsManager(
                User.withUsername(username).password(password).roles("OPERATOR").build()
        );
    }
}
