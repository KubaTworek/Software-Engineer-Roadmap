package pl.jakubtworek.lld.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Historia przyjętych działań, nie event sourcing. Sekwencja definiuje kolejność
 * także przy cofnięciu zegara. Zapisujemy rewizję, wykonawcę, etap i uzasadnienie;
 * treść historycznej rewizji jest przechowywana osobno w ContentRevision.
 */
public record WorkflowEvent(long sequence, Instant at, UserId actor, Type type, long revision, int round,
        Optional<String> stageId, Optional<String> note, DocumentStatus resultingStatus) {

    public enum Type {

        CREATED,
        EDITED,
        SUBMITTED,
        STAGE_APPROVED,
        REJECTED,
        WITHDRAWN
    }

    public WorkflowEvent {
        if (sequence < 1 || revision < 1 || round < 0)
            throw new IllegalArgumentException("Niepoprawne liczniki zdarzenia");
        Objects.requireNonNull(at);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(type);
        Objects.requireNonNull(stageId);
        Objects.requireNonNull(note);
        Objects.requireNonNull(resultingStatus);
        boolean stageRequired = type == Type.SUBMITTED || type == Type.STAGE_APPROVED || type == Type.REJECTED
                || type == Type.WITHDRAWN;
        if (stageRequired != stageId.isPresent())
            throw new IllegalArgumentException("Niespójny etap zdarzenia");
        if ((type == Type.REJECTED || type == Type.WITHDRAWN) != note.isPresent() || note.filter(String::isBlank)
                .isPresent()) {
            throw new IllegalArgumentException("Odrzucenie i wycofanie wymagają uzasadnienia");
        }
    }
}
