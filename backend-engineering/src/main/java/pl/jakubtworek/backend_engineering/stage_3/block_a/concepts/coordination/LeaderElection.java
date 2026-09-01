package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.coordination;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Leader election built on leases. It limits concurrent ownership at the
 * coordinator, but protected writes still need to enforce the returned term.
 */
public final class LeaderElection {

    private static final String RESOURCE_PREFIX = "leader/";

    private final InMemoryLeaseCoordinator coordinator;

    public LeaderElection(InMemoryLeaseCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
    }

    public Optional<LeadershipTerm> campaign(String election, String nodeId, Duration termDuration) {
        return coordinator.tryAcquire(resource(election), nodeId, termDuration)
                .map(LeadershipTerm::from);
    }

    public Optional<LeadershipTerm> heartbeat(LeadershipTerm term, Duration termDuration) {
        Objects.requireNonNull(term, "term must not be null");
        return coordinator.renew(term.asLease(), termDuration).map(LeadershipTerm::from);
    }

    public Optional<LeadershipTerm> currentLeader(String election) {
        return coordinator.currentLease(resource(election)).map(LeadershipTerm::from);
    }

    private static String resource(String election) {
        if (election == null || election.isBlank()) {
            throw new IllegalArgumentException("election is required");
        }
        return RESOURCE_PREFIX + election;
    }
}
