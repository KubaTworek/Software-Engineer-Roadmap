package pl.jakubtworek.backend.common.operations;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class ReplicaReadPolicyTest {
    private final ReplicaReadPolicy policy = new ReplicaReadPolicy();
    @Test void staleOrUnknownReplicaCannotConfirmFreshReservationView() {
        assertEquals(ReplicaReadPolicy.Target.REJECT, policy.select(false, true, Duration.ZERO, Duration.ofSeconds(2), true));
        assertEquals(ReplicaReadPolicy.Target.PRIMARY, policy.select(true, true, null, Duration.ofSeconds(2), false));
        assertEquals(ReplicaReadPolicy.Target.REJECT, policy.select(false, true, Duration.ofSeconds(3), Duration.ofSeconds(2), false));
        assertEquals(ReplicaReadPolicy.Target.REPLICA, policy.select(false, true, Duration.ofSeconds(2), Duration.ofSeconds(2), false));
    }
}
