package com.example.paymentsystem.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Verifies authenticity and a replay window before JSON is trusted. */
@Component
public class WebhookSignatureVerifier {
    private final List<byte[]> acceptedSecrets;
    private final Clock clock;

    @Autowired
    public WebhookSignatureVerifier(
            @Value("${payment-system.webhooks.mock-secret}") String currentSecret,
            @Value("${payment-system.webhooks.previous-secret:}") String previousSecret) {
        this(previousSecret.isBlank() ? List.of(currentSecret) : List.of(currentSecret, previousSecret),
                Clock.systemUTC());
    }

    WebhookSignatureVerifier(String secret, Clock clock) {
        this(List.of(secret), clock);
    }

    WebhookSignatureVerifier(List<String> secrets, Clock clock) {
        if (secrets.isEmpty() || secrets.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("At least one non-empty webhook secret is required");
        }
        this.acceptedSecrets = secrets.stream().map(value -> value.getBytes(StandardCharsets.UTF_8)).toList();
        this.clock = clock;
    }

    public void verify(long epochSeconds, String payload, String suppliedHex) {
        Instant signedAt = Instant.ofEpochSecond(epochSeconds);
        if (Duration.between(signedAt, clock.instant()).abs().compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Webhook timestamp is outside the replay window");
        }
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(suppliedHex);
        } catch (RuntimeException invalidHex) {
            throw new IllegalArgumentException("Invalid webhook signature");
        }
        boolean authentic = acceptedSecrets.stream()
                .map(secret -> signature(secret, epochSeconds, payload))
                .anyMatch(expected -> MessageDigest.isEqual(expected, supplied));
        if (!authentic) {
            throw new IllegalArgumentException("Invalid webhook signature");
        }
    }

    public String sign(long epochSeconds, String payload) {
        return HexFormat.of().formatHex(signature(acceptedSecrets.getFirst(), epochSeconds, payload));
    }

    private byte[] signature(byte[] secret, long epochSeconds, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal((epochSeconds + "." + payload).getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot calculate webhook signature", error);
        }
    }
}
