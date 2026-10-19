package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.time.Instant;

/** A leader is valid only for one lease term, identified by its fencing token. */
public record LeadershipTerm(
        String resource,
        String nodeId,
        long term,
        Instant expiresAt
) {

    public LeadershipTerm {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("resource is required");
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        if (term <= 0) {
            throw new IllegalArgumentException("term must be positive");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
    }

    static LeadershipTerm from(Lease lease) {
        return new LeadershipTerm(
                lease.resource(),
                lease.owner(),
                lease.fencingToken(),
                lease.expiresAt()
        );
    }

    Lease asLease() {
        return new Lease(resource, nodeId, term, expiresAt);
    }
}
