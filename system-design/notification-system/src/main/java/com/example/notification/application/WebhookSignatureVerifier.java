package com.example.notification.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Clock;
import java.util.HexFormat;

/** Weryfikuje dokładne bajty requestu i ogranicza clock skew; replay deduplikuje trwały inbox. */
@Component
public class WebhookSignatureVerifier {
    private final byte[] secret;
    private final byte[] previousSecret;
    private final Clock clock;

    @Autowired
    public WebhookSignatureVerifier(@Value("${notification.webhook.signing-secret}") String secret,
                                    @Value("${notification.webhook.previous-signing-secret:}") String previousSecret,
                                    Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.previousSecret = previousSecret == null || previousSecret.isBlank()
                ? null : previousSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }
    public WebhookSignatureVerifier(String secret) { this(secret, "", Clock.systemUTC()); }

    public void verify(long timestamp, String signature, String rawBody) {
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > 300) {
            throw new Exceptions.NotificationValidationException("Webhook timestamp outside accepted window");
        }
        String expected = sign(secret, timestamp, rawBody);
        boolean currentMatches = signature != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
        boolean previousMatches = signature != null && previousSecret != null
                && MessageDigest.isEqual(sign(previousSecret, timestamp, rawBody).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
        if (!currentMatches && !previousMatches) {
            throw new Exceptions.NotificationValidationException("Invalid webhook signature");
        }
        // Deduplikacja nie należy do kryptografii. Trwały webhook inbox zapisuje
        // eventId i idempotentnie potwierdza legalne ponowne dostarczenie providera.
    }

    public String sign(long timestamp, String rawBody) {
        return sign(secret, timestamp, rawBody);
    }

    private String sign(byte[] key, long timestamp, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot calculate webhook signature", ex);
        }
    }
}
