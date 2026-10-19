package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Provides an ephemeral RSA key pair for this self-contained learning example.
 *
 * <p>A production application should load signing keys from a secrets manager or
 * delegate token creation to a dedicated authorization server. Generating keys
 * on startup invalidates every token after an application restart.</p>
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    KeyPair jwtKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    PrivateKey jwtPrivateKey(KeyPair jwtKeyPair) {
        return jwtKeyPair.getPrivate();
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair jwtKeyPair) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) jwtKeyPair.getPublic())
                .build();
        var issuerValidator = JwtValidators.createDefaultWithIssuer("demo-auth-server");
        var audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audience -> audience != null && audience.contains("backend-api")
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }
}
