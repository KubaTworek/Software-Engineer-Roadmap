package com.ridesharing.mvp.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stabilny kontrakt transportowy zdarzenia. Metadane nie są ukryte w nazwie
 * topicu ani w rekordzie outbox, dzięki czemu każdy konsument może deduplikować,
 * wersjonować i korelować wiadomości niezależnie od payloadu domenowego.
 */
public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        int schemaVersion,
        Instant occurredAt,
        String correlationId,
        String causationId,
        Map<String, Object> payload
) {
}
