package pl.jakubtworek.lld.workflow.domain;

/**
 * REJECTED pozwala poprawić i ponownie złożyć; APPROVED jest końcem tego workflow.
 */
public enum DocumentStatus {

    DRAFT, IN_REVIEW, REJECTED, APPROVED
}
