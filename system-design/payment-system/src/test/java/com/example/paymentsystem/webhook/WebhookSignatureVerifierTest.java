package com.example.paymentsystem.webhook;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier("secret", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsAuthenticPayloadInsideReplayWindow() {
        String body = "{\"eventId\":\"evt-1\"}";
        String signature = verifier.sign(NOW.getEpochSecond(), body);
        assertThatCode(() -> verifier.verify(NOW.getEpochSecond(), body, signature)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperingAndOldReplay() {
        String signature = verifier.sign(NOW.getEpochSecond(), "original");
        assertThatThrownBy(() -> verifier.verify(NOW.getEpochSecond(), "changed", signature))
                .isInstanceOf(IllegalArgumentException.class);

        long old = NOW.minusSeconds(301).getEpochSecond();
        assertThatThrownBy(() -> verifier.verify(old, "body", verifier.sign(old, "body")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedSignatureEncoding() {
        assertThatThrownBy(() -> verifier.verify(NOW.getEpochSecond(), "body", "not-hex"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid webhook signature");
    }

    @Test
    void acceptsPreviousKeyDuringRotationButSignsOnlyWithCurrentKey() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        WebhookSignatureVerifier rotating = new WebhookSignatureVerifier(List.of("new-secret", "old-secret"), clock);
        WebhookSignatureVerifier old = new WebhookSignatureVerifier("old-secret", clock);
        String payload = "{\"event\":\"payment.succeeded\"}";
        long timestamp = clock.instant().getEpochSecond();

        rotating.verify(timestamp, payload, old.sign(timestamp, payload));
        assertThat(rotating.sign(timestamp, payload)).isNotEqualTo(old.sign(timestamp, payload));
    }
}
