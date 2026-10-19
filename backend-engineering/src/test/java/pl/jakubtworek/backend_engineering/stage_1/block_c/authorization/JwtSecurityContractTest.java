package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityContractTest {

    @Test
    void shouldMapTheApplicationClaimsToExactAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .claim("roles", List.of("ADMIN"))
                .claim("permissions", List.of("ORDER_READ", "ORDER_WRITE"))
                .build();

        var authentication = new JwtAuthoritiesConverter().convert(jwt);

        assertThat(authentication.getName()).isEqualTo("alice");
        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ORDER_READ", "ORDER_WRITE");
    }

    @Test
    void shouldRejectATokenIssuedForAnotherAudience() throws Exception {
        JwtKeyConfig config = new JwtKeyConfig();
        KeyPair keyPair = config.jwtKeyPair();
        JwtDecoder decoder = config.jwtDecoder(keyPair);
        Instant now = Instant.now();
        String wrongAudienceToken = Jwts.builder()
                .subject("alice")
                .issuer("demo-auth-server")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .claim("aud", List.of("another-api"))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> decoder.decode(wrongAudienceToken))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldAcceptATokenOnlyWhenSignatureIssuerAndAudienceMatch() throws Exception {
        JwtKeyConfig config = new JwtKeyConfig();
        KeyPair keyPair = config.jwtKeyPair();
        JwtDecoder decoder = config.jwtDecoder(keyPair);
        String token = new JwtTokenService(keyPair.getPrivate()).generateAccessToken(
                "alice",
                List.of("USER"),
                List.of("ORDER_READ")
        );

        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("alice");
        assertThat(decoded.getAudience()).containsExactly("backend-api");
        assertThat(decoded.getId()).isNotBlank();
    }
}
