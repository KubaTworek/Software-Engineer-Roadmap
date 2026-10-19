package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    @Test
    void shouldGenerateVerifiableTokenWithExpectedIdentityAndAuthorities() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        JwtTokenService service = new JwtTokenService(keyPair.getPrivate());

        String token = service.generateAccessToken(
                "alice",
                List.of("USER"),
                List.of("ORDER_READ")
        );

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getIssuer()).isEqualTo("demo-auth-server");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getAudience()).containsExactly("backend-api");
        assertThat(claims.get("roles")).isEqualTo(List.of("USER"));
        assertThat(claims.get("permissions")).isEqualTo(List.of("ORDER_READ"));
        assertThat(claims.getIssuedAt()).isBeforeOrEqualTo(claims.getExpiration());
        assertThat(Duration.between(
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        )).isEqualTo(Duration.ofMinutes(15));
        assertThat(claims.getExpiration().toInstant()).isAfter(Instant.now());
    }
}
