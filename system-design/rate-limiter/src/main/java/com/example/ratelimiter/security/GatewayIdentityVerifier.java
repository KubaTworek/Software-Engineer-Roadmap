package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies identity assertions made by a gateway. A private source address is
 * only a network boundary, not cryptographic proof: another workload in the
 * same network could otherwise promote itself to another tenant or plan.
 *
 * The timestamp is part of the signed payload, so a captured request expires.
 * Production deployments normally replace the shared secret with mTLS or a
 * workload-identity token; this class makes the missing trust boundary
 * executable without pretending to be a complete IAM system.
 */
@Component
public class GatewayIdentityVerifier {
    private final RateLimiterProperties properties;
    private final Clock clock;

    @Autowired
    public GatewayIdentityVerifier(RateLimiterProperties properties) {
        this(properties, Clock.systemUTC());
    }

    GatewayIdentityVerifier(RateLimiterProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isValid(HttpServletRequest request) {
        var security = properties.getSecurity();
        if (!security.isRequireSignedIdentityHeaders()) return true;
        String timestamp = request.getHeader(security.getIdentityTimestampHeader());
        String signature = request.getHeader(security.getIdentitySignatureHeader());
        if (timestamp == null || signature == null) return false;
        try {
            long epochSeconds = Long.parseLong(timestamp);
            long age = Math.abs(Instant.now(clock).getEpochSecond() - epochSeconds);
            if (age > security.getIdentitySignatureMaxAgeSeconds()) return false;
            String expected = signatureFor(request, timestamp);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    String signatureFor(HttpServletRequest request, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecurity().getGatewayAssertionHmacSecret()
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    canonical(request, timestamp).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign gateway identity", exception);
        }
    }

    private String canonical(HttpServletRequest request, String timestamp) {
        var security = properties.getSecurity();
        return String.join("\n",
                timestamp,
                request.getMethod(),
                request.getRequestURI(),
                value(request.getHeader(security.getTenantHeader())),
                value(request.getHeader(security.getUserHeader())),
                value(request.getHeader(security.getPlanHeader())));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
