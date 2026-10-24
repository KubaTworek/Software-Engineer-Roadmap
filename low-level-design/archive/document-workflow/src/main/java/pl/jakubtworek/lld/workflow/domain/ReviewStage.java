package pl.jakubtworek.lld.workflow.domain;

import java.util.Objects;

/**
 * Etap wymaga jednej decyzji osoby z daną rolą. Nie przypisujemy konkretnego
 * konta ani quorum. AUTHOR nie jest rolą przeglądu: autorstwo i decyzja są rozdzielone.
 */
public record ReviewStage(String id, Role requiredRole) {

    public ReviewStage {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,39}"))
            throw new IllegalArgumentException("Niepoprawne ID etapu");
        Objects.requireNonNull(requiredRole);
        if (requiredRole == Role.AUTHOR)
            throw new IllegalArgumentException("Etap nie może wymagać roli AUTHOR");
    }
}
