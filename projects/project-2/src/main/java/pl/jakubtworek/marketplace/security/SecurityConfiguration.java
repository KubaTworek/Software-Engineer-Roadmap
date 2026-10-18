package pl.jakubtworek.marketplace.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Wykonywalna, fail-closed granica bezpieczeństwa aktywowana profilem {@code secure}.
 * JWT uwierzytelnia caller, reguły URL chronią operacje administracyjne, a polityka
 * własności na metodach blokuje IDOR. CSRF jest wyłączone wyłącznie dlatego, że API
 * jest bezstanowe i używa Bearer tokenu, a nie automatycznych cookies przeglądarki.
 */
@Configuration @Profile("secure") @EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .sessionManagement(it -> it.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/admin/**").hasAuthority("SCOPE_admin")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> { }))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${marketplace.security.jwt-secret}") String secret) {
        if (secret.length() < 32) throw new IllegalStateException("JWT secret must contain at least 32 characters");
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
    }
}
