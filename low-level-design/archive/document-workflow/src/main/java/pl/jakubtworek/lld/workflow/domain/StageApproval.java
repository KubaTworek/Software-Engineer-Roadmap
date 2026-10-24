package pl.jakubtworek.lld.workflow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Fakt zatwierdzenia etapu w rundzie. Zatwierdzonej rewizji szukamy w ReviewRound.
 */
public record StageApproval(String stageId, UserId reviewer, Instant at) {

    public StageApproval {
        Objects.requireNonNull(stageId);
        Objects.requireNonNull(reviewer);
        Objects.requireNonNull(at);
    }
}
