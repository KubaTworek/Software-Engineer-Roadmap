package com.example.notification;

import com.example.notification.application.Exceptions;
import com.example.notification.application.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class WebhookSignatureVerifierTest {
    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("test-secret");

    @Test void validatesExactBodyAndAllowsInboxToDeduplicateDelivery() {
        long timestamp = Instant.now().getEpochSecond();
        String body = "{\"status\":\"DELIVERED\"}";
        String signature = verifier.sign(timestamp, body);
        verifier.verify(timestamp, signature, body);
        assertThatCode(() -> verifier.verify(timestamp, signature, body)).doesNotThrowAnyException();
    }

    @Test void rejectsTamperingAndOldTimestamp() {
        long timestamp = Instant.now().getEpochSecond();
        String signature = verifier.sign(timestamp, "original");
        assertThatThrownBy(() -> verifier.verify(timestamp, signature, "changed"))
                .isInstanceOf(Exceptions.NotificationValidationException.class);
        long old = timestamp - 301;
        assertThatThrownBy(() -> verifier.verify(old, verifier.sign(old, "body"), "body"))
                .hasMessageContaining("timestamp");
    }
}
