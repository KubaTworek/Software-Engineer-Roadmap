package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event;

import java.time.Instant;

/** Stable contract published outside the Sales module. */
public interface IntegrationEvent {

    String messageId();

    String aggregateId();

    String eventType();

    int schemaVersion();

    Instant occurredAt();
}
