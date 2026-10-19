package com.example.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Produkcyjna granica uwierzytelnienia laboratorium. Profil używa podpisanego JWT;
 * tenant oraz role są claims tokenu, a nie wartościami wybieranymi przez klienta.
 */
@Configuration
@Profile("secure-runtime")
public class SecurityConfiguration {
    @Bean SecurityFilterChain notificationSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/provider-webhooks/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    var authorities = token.getClaimAsStringList("roles");
                    var granted = authorities == null ? java.util.List.<org.springframework.security.core.GrantedAuthority>of()
                            : authorities.stream().map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList();
                    return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token, granted);
                }))).build();
    }

    @Bean JwtDecoder jwtDecoder(@Value("${notification.security.jwt-secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("notification.security.jwt-secret must have at least 32 bytes");
        }
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
    }
}
