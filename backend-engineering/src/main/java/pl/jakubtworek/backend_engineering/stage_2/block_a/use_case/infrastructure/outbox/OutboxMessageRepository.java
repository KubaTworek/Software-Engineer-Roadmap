package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox;

import java.util.List;

// Repository for outbox messages.
// This is an infrastructure repository, not a domain repository.
public interface OutboxMessageRepository {

    void save(OutboxMessage message);

    List<OutboxMessage> findUnpublished(int limit);
}