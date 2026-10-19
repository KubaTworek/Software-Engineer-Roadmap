package com.example.observability.server.alert;

import java.time.Instant;
import java.util.UUID;

/** Czasowe wyciszenie dostarczenia; nie zatrzymuje ewaluacji ani historii stanu. */
public record AlertSilence(String id, String tenantId, String ruleId, Instant startsAt,
                           Instant endsAt, String reason) {
    public AlertSilence {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (startsAt == null) startsAt = Instant.now();
        if (endsAt == null || !endsAt.isAfter(startsAt))
            throw new IllegalArgumentException("silence end must be after start");
    }
}
