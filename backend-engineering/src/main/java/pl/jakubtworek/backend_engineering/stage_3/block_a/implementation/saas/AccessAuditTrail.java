package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Append-only audit model. Events contain references and purpose, but no copied PII. */
public final class AccessAuditTrail {

    private final Clock clock;
    private final List<AccessAuditEvent> events = new CopyOnWriteArrayList<>();

    public AccessAuditTrail(Clock clock) {
        this.clock = clock;
    }

    public void record(TenantRequestContext context, String action, String subjectId, Outcome outcome) {
        events.add(new AccessAuditEvent(
                Instant.now(clock), context.tenantId(), context.actorId(), action, subjectId, context.purpose(), outcome));
    }

    public List<AccessAuditEvent> events() {
        return List.copyOf(events);
    }

    public enum Outcome {
        ALLOWED,
        DENIED
    }

    public record AccessAuditEvent(
            Instant occurredAt,
            TenantId tenantId,
            String actorId,
            String action,
            String subjectReference,
            String purpose,
            Outcome outcome) {
    }
}
