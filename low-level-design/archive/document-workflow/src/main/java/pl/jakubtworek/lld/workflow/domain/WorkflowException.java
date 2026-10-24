package pl.jakubtworek.lld.workflow.domain;

/**
 * Kod odmowy jest kontraktem dla testów/adaptera; tekst można tłumaczyć niezależnie.
 */
public final class WorkflowException extends IllegalArgumentException {

    public enum Reason {

        FORBIDDEN,
        WRONG_STATE,
        SELF_REVIEW,
        STALE_REVIEW,
        REASON_REQUIRED,
        NEW_REVISION_REQUIRED,
        UNCHANGED_CONTENT,
        STALE_VERSION,
        REVIEWER_ALREADY_USED
    }

    private final Reason reason;

    public WorkflowException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
