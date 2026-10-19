package com.example.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Niezmienny zapis pojedynczego wywołania providera.
 *
 * <p>Job opisuje logiczną dostawę jednym kanałem, natomiast attempt odpowiada
 * dokładnie jednemu efektowi na granicy sieciowej. To rozróżnienie pozwala
 * wyjaśnić, którego providera wywołano, czy bezpieczny jest fallback oraz
 * dlaczego wynik UNKNOWN wymaga rekoncyliacji zamiast ślepego retry.</p>
 */
public record DeliveryAttempt(
        UUID id,
        UUID jobId,
        int number,
        String providerName,
        Outcome outcome,
        String providerMessageId,
        String error,
        Instant occurredAt
) {
    public enum Outcome { ACCEPTED, NOT_SENT, UNKNOWN, REJECTED }

    public DeliveryAttempt {
        if (id == null || jobId == null || providerName == null || outcome == null || occurredAt == null) {
            throw new IllegalArgumentException("Attempt identity, provider, outcome and time are required");
        }
        if (number < 1) throw new IllegalArgumentException("Attempt number must be positive");
    }
}
