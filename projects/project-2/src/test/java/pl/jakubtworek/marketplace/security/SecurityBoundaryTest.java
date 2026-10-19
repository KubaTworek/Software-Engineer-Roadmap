package pl.jakubtworek.marketplace.security;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityBoundaryTest {
    @Test void blocksAllowlistedHostnameWhenDnsPointsToLoopback() throws Exception {
        var policy = new OutboundRequestPolicy(Set.of("hooks.partner.example"));
        assertThatThrownBy(() -> policy.verify(URI.create("https://hooks.partner.example/order"),
                InetAddress.getByName("127.0.0.1"))).isInstanceOf(SecurityException.class);
    }

    @Test void generatedStorageKeyCannotEscapeUploadRoot() {
        var policy = new UploadPolicy(1024, Set.of("image/png"));
        assertThatThrownBy(() -> policy.safeTarget(Path.of("uploads"), "../secret", "image/png", 10))
                .isInstanceOf(SecurityException.class);
    }

    @Test void acceptsAuthenticWebhookOnceAndRejectsReplay() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        byte[] body = "{\"orderId\":\"o-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var verifier = new WebhookSignatureVerifier(Set.of("rotated-secret"), Duration.ofMinutes(5));
        String signature = WebhookSignatureVerifier.signature("rotated-secret", now, body);
        verifier.verify("delivery-1", now, body, signature, now);
        assertThatThrownBy(() -> verifier.verify("delivery-1", now, body, signature, now))
                .isInstanceOf(SecurityException.class).hasMessageContaining("replayed");
    }
}
