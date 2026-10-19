package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.Objects;

public record TenantDataRecord(TenantId tenantId, String subjectId, String email, String displayName) {

    public TenantDataRecord {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
    }

    public TenantDataRecord anonymized() {
        return new TenantDataRecord(tenantId, subjectId, "erased@example.invalid", "ERASED");
    }
}
