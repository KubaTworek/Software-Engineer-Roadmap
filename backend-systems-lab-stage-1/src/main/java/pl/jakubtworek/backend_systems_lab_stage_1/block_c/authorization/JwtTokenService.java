package pl.jakubtworek.backend_systems_lab_stage_1.block_c.authorization;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Responsible for generating access tokens.
 *
 * Access token should be short-lived.
 * Usually it contains:
 * - subject,
 * - expiration,
 * - roles,
 * - permissions,
 * - issuer,
 * - token id.
 */
@Service
public class JwtTokenService {

    private final PrivateKey privateKey;

    public JwtTokenService(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Generates signed JWT access token.
     *
     * RS256 means:
     * - private key signs the token,
     * - public key verifies the token.
     */
    public String generateAccessToken(
            String username,
            List<String> roles,
            List<String> permissions
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
                .setSubject(username)
                .setIssuer("demo-auth-server")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(15 * 60)))
                .claim("roles", roles)
                .claim("permissions", permissions)
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
}