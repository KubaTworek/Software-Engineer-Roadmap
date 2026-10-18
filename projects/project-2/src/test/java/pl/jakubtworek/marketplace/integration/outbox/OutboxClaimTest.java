package pl.jakubtworek.marketplace.integration.outbox;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.integration.outbox.infrastructure.InMemoryOutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxClaimTest {
    @Test void twoWorkersCannotClaimTheSamePendingEvent() {
        var repository = new InMemoryOutboxEventRepository();
        repository.save(new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), "Order", "OrderPlaced", 1,
                "{}", UUID.randomUUID(), null, OutboxEventStatus.NEW, Instant.now(), null, 0, null));

        var firstWorker = repository.claimNew(10);
        var secondWorker = repository.claimNew(10);

        assertThat(firstWorker).hasSize(1).allMatch(event -> event.status() == OutboxEventStatus.PROCESSING);
        assertThat(secondWorker).isEmpty();
    }
}
