package pl.jakubtworek.backend.notification.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import pl.jakubtworek.backend.common.events.OrderPaidEvent;
import pl.jakubtworek.backend.notification.chaos.NotificationChaosSettings;
import pl.jakubtworek.backend.notification.persistence.ProcessedMessageRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testuje granicę at-least-once: stabilny messageId jest warunkiem idempotencji,
 * a redelivery oznaczony istniejącym markerem nie może powtórzyć efektu.
 */
class NotificationListenerTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void ignoresRedeliveryAlreadyClaimedByInbox() throws Exception {
        UUID messageId = UUID.randomUUID();
        ProcessedMessageRepository repository = mock(ProcessedMessageRepository.class);
        when(repository.tryMarkProcessed(messageId, NOW)).thenReturn(0);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        NotificationListener listener = listener(repository, meters);

        listener.onOrderPaid(event(), message(messageId));

        assertThat(meters.counter("app_notifications_sent_total").count()).isZero();
        verify(repository).tryMarkProcessed(messageId, NOW);
    }

    @Test
    void failsClosedWhenBrokerMessageHasNoStableIdentity() {
        NotificationListener listener = listener(mock(ProcessedMessageRepository.class), new SimpleMeterRegistry());

        assertThatThrownBy(() -> listener.onOrderPaid(event(), new Message(new byte[0], new MessageProperties())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }

    private NotificationListener listener(ProcessedMessageRepository repository, SimpleMeterRegistry meters) {
        return new NotificationListener(new NotificationChaosSettings(0), meters, repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Message message(UUID id) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(id.toString());
        return new Message(new byte[0], properties);
    }

    private OrderPaidEvent event() {
        return OrderPaidEvent.at(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "user-1",
                BigDecimal.TEN, NOW, "correlation-1", "request-1", "trace-1");
    }
}
