package com.example.ecommerce.payment;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.monitoring.BusinessMetrics;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Weryfikuje callback przed deserializacją i efektem biznesowym. Podpis obejmuje
 * surowe body oraz timestamp, dlatego zmiana whitespace również unieważnia HMAC.
 */
@Component
public class PaymentWebhookVerifier {
    private final PaymentWebhookProperties properties;
    private final BusinessMetrics metrics;
    private final Clock clock;

    public PaymentWebhookVerifier(
            PaymentWebhookProperties properties,
            BusinessMetrics metrics,
            Clock clock
    ) {
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void verify(byte[] body, long epochSeconds, String signature) {
        Instant sentAt = Instant.ofEpochSecond(epochSeconds);
        Duration age = Duration.between(sentAt, clock.instant()).abs();
        if (age.compareTo(properties.getMaxWebhookAge()) > 0) {
            reject("Payment webhook timestamp is outside the accepted window");
        }
        byte[] expected = hmac(epochSeconds + "." + new String(body, StandardCharsets.UTF_8));
        byte[] supplied;
        try {
            supplied = HexFormat.of().parseHex(signature == null ? "" : signature);
        } catch (IllegalArgumentException malformed) {
            reject("Payment webhook signature is malformed");
            return;
        }
        if (!MessageDigest.isEqual(expected, supplied)) {
            reject("Payment webhook signature is invalid");
        }
    }

    byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not verify payment webhook", failure);
        }
    }

    private void reject(String message) {
        metrics.webhookRejected();
        throw ApiException.forbidden(message);
    }
}
