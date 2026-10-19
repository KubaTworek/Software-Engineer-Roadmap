package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Creates a non-reversible, deployment-scoped marker without storing API keys. */
@Component
public class ApiKeyHasher {
    private final byte[] secret;

    public ApiKeyHasher(RateLimiterProperties properties) {
        this.secret = properties.getSecurity().getIdentityHmacSecret().getBytes(StandardCharsets.UTF_8);
    }

    public String hmacSha256(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }
}
