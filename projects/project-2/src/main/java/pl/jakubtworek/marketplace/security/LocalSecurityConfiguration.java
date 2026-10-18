package pl.jakubtworek.marketplace.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Jawna granica laboratorium: lokalny profil pozostaje wygodny, lecz środowisko
 * wdrożeniowe musi aktywować {@code secure}. Rozdzielenie zapobiega przypadkowemu
 * poleganiu na losowym haśle generowanym przez auto-konfigurację Spring Security.
 */
@Configuration @Profile("!secure")
public class LocalSecurityConfiguration {
    @Bean SecurityFilterChain localSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
