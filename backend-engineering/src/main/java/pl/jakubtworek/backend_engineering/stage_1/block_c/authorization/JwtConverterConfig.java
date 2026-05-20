package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Registers custom JWT authority mapping.
 *
 * Without custom mapping, Spring may not know how to convert
 * application-specific claims like "roles" or "permissions"
 * into GrantedAuthority objects.
 */
@Configuration
public class JwtConverterConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        /**
         * In real code you can use JwtGrantedAuthoritiesConverter
         * or a custom converter depending on JWT structure.
         */
        return converter;
    }
}