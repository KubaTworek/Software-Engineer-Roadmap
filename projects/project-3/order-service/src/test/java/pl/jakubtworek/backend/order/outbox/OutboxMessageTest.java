package pl.jakubtworek.backend.order.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageTest {
    @Test
    void poisonMessageBecomesOperatorVisibleInsteadOfRetryingForever() {
        OutboxMessage message = new OutboxMessage(UUID.randomUUID(), "order", "order.paid.v1", "{broken", Instant.EPOCH);

        for (int attempt = 0; attempt < 10; attempt++) {
            message.scheduleRetry(Instant.EPOCH.plusSeconds(attempt), "cannot deserialize");
        }

        assertThat(message.getPublishAttempts()).isEqualTo(10);
        assertThat(message.getDeadAt()).isEqualTo(Instant.EPOCH.plusSeconds(9));
        assertThat(message.getNextAttemptAt()).isNull();
    }
}
