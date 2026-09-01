package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookDeliveryTest {

    @Test
    void retryPreservesDeliveryIdentityPayloadTimestampAndSignature() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-01T10:00:00Z"));
        UUID deliveryId = UUID.fromString("00000000-0000-0000-0000-000000000031");
        List<WebhookDeliveryService.WebhookRequest> attempts = new ArrayList<>();
        WebhookDeliveryService service = new WebhookDeliveryService(
                clock,
                () -> deliveryId,
                new HmacWebhookSigner("a-secure-laboratory-secret-with-32-bytes".getBytes()),
                request -> {
                    attempts.add(request);
                    return attempts.size() == 2;
                },
                3
        );
        WebhookDeliveryService.Delivery delivery = service.enqueue(
                "order.cancelled", "{\"orderId\":\"order-31\"}");

        WebhookDeliveryService.Delivery failed = service.attempt(delivery.id());
        WebhookDeliveryService.Delivery tooEarly = service.attempt(delivery.id());
        clock.advance(Duration.ofSeconds(1));
        WebhookDeliveryService.Delivery delivered = service.attempt(delivery.id());

        assertThat(failed.state()).isEqualTo(WebhookDeliveryService.State.PENDING);
        assertThat(tooEarly.attempts()).isEqualTo(1);
        assertThat(delivered.state()).isEqualTo(WebhookDeliveryService.State.DELIVERED);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(1)).isEqualTo(attempts.get(0));
        assertThat(attempts.get(0).signature()).startsWith("v1=");
    }

    @Test
    void deliveryBecomesExhaustedAfterTheConfiguredAttemptBudget() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-01T10:00:00Z"));
        WebhookDeliveryService service = new WebhookDeliveryService(
                clock,
                UUID::randomUUID,
                new HmacWebhookSigner("a-secure-laboratory-secret-with-32-bytes".getBytes()),
                ignored -> false,
                2
        );
        WebhookDeliveryService.Delivery delivery = service.enqueue("order.cancelled", "{}");

        service.attempt(delivery.id());
        clock.advance(Duration.ofSeconds(1));
        WebhookDeliveryService.Delivery exhausted = service.attempt(delivery.id());

        assertThat(exhausted.state()).isEqualTo(WebhookDeliveryService.State.EXHAUSTED);
        assertThat(exhausted.attempts()).isEqualTo(2);
    }

    @Test
    void transportExceptionConsumesAnAttemptAndSchedulesRedelivery() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-01T10:00:00Z"));
        WebhookDeliveryService service = new WebhookDeliveryService(
                clock,
                UUID::randomUUID,
                new HmacWebhookSigner("a-secure-laboratory-secret-with-32-bytes".getBytes()),
                ignored -> { throw new IllegalStateException("connection reset"); },
                3
        );
        WebhookDeliveryService.Delivery delivery = service.enqueue("order.cancelled", "{}");

        WebhookDeliveryService.Delivery failed = service.attempt(delivery.id());

        assertThat(failed.state()).isEqualTo(WebhookDeliveryService.State.PENDING);
        assertThat(failed.attempts()).isEqualTo(1);
        assertThat(failed.nextAttemptAt()).isAfter(clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("laboratory clock uses UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
