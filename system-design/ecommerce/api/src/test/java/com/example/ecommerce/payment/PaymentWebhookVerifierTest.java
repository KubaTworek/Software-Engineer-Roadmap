package com.example.ecommerce.payment;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.monitoring.BusinessMetrics;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentWebhookVerifierTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Test
    void acceptsAuthenticRecentBodyAndRejectsTamperingAndReplay() {
        PaymentWebhookProperties properties = new PaymentWebhookProperties();
        properties.setWebhookSecret("secret");
        properties.setMaxWebhookAge(Duration.ofMinutes(5));
        PaymentWebhookVerifier verifier = new PaymentWebhookVerifier(
                properties, mock(BusinessMetrics.class), Clock.fixed(NOW, ZoneOffset.UTC));
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        long timestamp = NOW.getEpochSecond();
        String signature = HexFormat.of().formatHex(verifier.hmac(timestamp + "." + new String(body, StandardCharsets.UTF_8)));

        assertThatCode(() -> verifier.verify(body, timestamp, signature)).doesNotThrowAnyException();
        assertThatThrownBy(() -> verifier.verify("changed".getBytes(StandardCharsets.UTF_8), timestamp, signature))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.verify(body, timestamp - 301, signature))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("timestamp");
    }
}
