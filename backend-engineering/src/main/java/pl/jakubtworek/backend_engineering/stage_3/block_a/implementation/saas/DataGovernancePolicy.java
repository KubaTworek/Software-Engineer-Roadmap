package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.time.Duration;

public record DataGovernancePolicy(
        String field,
        Classification classification,
        Duration retention,
        ErasureMode erasureMode,
        String purpose) {

    public DataGovernancePolicy {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (classification == null || retention == null || erasureMode == null) {
            throw new IllegalArgumentException("classification, retention and erasureMode are required");
        }
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("retention must be positive");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("processing purpose must be explicit");
        }
        if (erasureMode == ErasureMode.RETAIN_FOR_LEGAL_OBLIGATION
                && !purpose.toLowerCase().startsWith("legal-obligation:")) {
            throw new IllegalArgumentException("retention after erasure requires an explicit legal-obligation purpose");
        }
    }

    public enum Classification {
        PUBLIC(false),
        INTERNAL(false),
        PII(true),
        SENSITIVE_PII(true);

        private final boolean personal;

        Classification(boolean personal) {
            this.personal = personal;
        }

        public boolean isPersonal() {
            return personal;
        }
    }

    public enum ErasureMode {
        DELETE,
        ANONYMIZE,
        RETAIN_FOR_LEGAL_OBLIGATION
    }
}
