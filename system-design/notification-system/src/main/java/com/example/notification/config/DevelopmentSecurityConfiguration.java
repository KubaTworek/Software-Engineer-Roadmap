package com.example.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Zachowuje szybki profil dydaktyczny bez generowania losowego hasła Spring Security. */
@Configuration
@Profile("!secure-runtime")
public class DevelopmentSecurityConfiguration {
    @Bean SecurityFilterChain educationalSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
