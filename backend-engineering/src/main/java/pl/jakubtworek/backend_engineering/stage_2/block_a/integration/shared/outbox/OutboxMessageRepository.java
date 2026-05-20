package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.outbox;

import java.util.List;

// Repository for outbox messages.
// It belongs to infrastructure, not to the domain model.
public interface OutboxMessageRepository {

    void save(OutboxMessage message);

    List<OutboxMessage> findUnpublished(int limit);

    void update(OutboxMessage message);
}