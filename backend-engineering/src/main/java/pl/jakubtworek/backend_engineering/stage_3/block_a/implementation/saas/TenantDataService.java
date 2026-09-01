package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.NoSuchElementException;

/** Enforces tenant scope before returning data and audits both successful and denied access. */
public final class TenantDataService {

    private final TenantDataRepository repository;
    private final AccessAuditTrail auditTrail;

    public TenantDataService(TenantDataRepository repository, AccessAuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    public TenantDataRecord read(TenantRequestContext context, TenantId requestedTenant, String subjectId) {
        if (!context.tenantId().equals(requestedTenant)) {
            auditTrail.record(context, "subject.read", subjectId, AccessAuditTrail.Outcome.DENIED);
            throw new TenantIsolationException("cross-tenant access denied");
        }

        TenantDataRecord result = repository.find(requestedTenant, subjectId)
                .orElseThrow(() -> new NoSuchElementException("subject not found"));
        auditTrail.record(context, "subject.read", subjectId, AccessAuditTrail.Outcome.ALLOWED);
        return result;
    }

    public static final class TenantIsolationException extends RuntimeException {
        public TenantIsolationException(String message) {
            super(message);
        }
    }
}
