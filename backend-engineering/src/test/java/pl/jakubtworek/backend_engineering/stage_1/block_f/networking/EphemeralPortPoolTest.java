package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EphemeralPortPoolTest {

    @Test
    void shortLivedConnectionsExhaustPortsUntilTimeWaitExpires() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        EphemeralPortPool ports = new EphemeralPortPool(clock, 50_000, 50_001, Duration.ofSeconds(30));
        int first = ports.openConnection().orElseThrow();
        int second = ports.openConnection().orElseThrow();
        ports.closeConnection(first);
        ports.closeConnection(second);

        assertThat(ports.openConnection()).isEmpty();
        assertThat(ports.unavailablePorts()).isEqualTo(2);
        clock.advance(Duration.ofSeconds(30));
        assertThat(ports.openConnection()).isPresent();
    }
}
