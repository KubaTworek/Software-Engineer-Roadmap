package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.messaging;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.infrastructure.persistance.OutboxMessage;

import java.util.List;

// Repository for outbox messages.
// This is an infrastructure concern, not a domain repository.
public interface OutboxMessageRepository {

    List<OutboxMessage> findUnpublished();

    void save(OutboxMessage message);
}