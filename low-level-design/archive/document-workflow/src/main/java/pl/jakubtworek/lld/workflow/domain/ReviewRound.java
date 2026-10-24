package pl.jakubtworek.lld.workflow.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Runda zamraża złożoną rewizję i treść. Po odrzuceniu autor może edytować
 * dokument, ale złożony materiał tej rundy nie zmieni się wstecznie. Nowa runda
 * otrzyma nową pustą listę akceptacji, zamiast dziedziczyć stare decyzje.
 */
public record ReviewRound(int number, long submittedRevision, DocumentContent submittedContent,
        List<StageApproval> approvals) {

    public ReviewRound {
        if (number < 1 || submittedRevision < 1)
            throw new IllegalArgumentException("Niepoprawna runda");
        Objects.requireNonNull(submittedContent);
        approvals = List.copyOf(approvals);
        var stages = new HashSet<String>();
        for (StageApproval approval : approvals) if (!stages.add(approval.stageId()))
            throw new IllegalArgumentException("Etap zatwierdzony dwukrotnie");
    }
}
