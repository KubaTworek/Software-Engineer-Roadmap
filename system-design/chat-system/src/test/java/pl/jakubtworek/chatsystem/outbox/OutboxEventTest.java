package pl.jakubtworek.chatsystem.outbox;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {
    @Test
    void marksEventLifecycle() {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), EventTypes.MESSAGE_CREATED, "{}");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.NEW);

        event.markEnqueued();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.ENQUEUED);
        assertThat(event.getAttempts()).isEqualTo(1);

        event.markPublished();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }
}
