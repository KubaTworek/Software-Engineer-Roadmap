package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConnectionPoolTest {

    @Test
    void reusesCleanKeepAliveConnectionAndEnforcesBound() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        ConnectionPool pool = new ConnectionPool(clock, 1, Duration.ofSeconds(30), Duration.ofMinutes(5));

        ConnectionPool.Connection first = pool.tryAcquire().orElseThrow();
        assertThat(pool.tryAcquire()).isEmpty();
        pool.release(first, true);

        assertThat(pool.tryAcquire().orElseThrow().id()).isEqualTo(first.id());
    }

    @Test
    void discardsConnectionAfterIncompleteResponseOrIdleExpiry() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        ConnectionPool pool = new ConnectionPool(clock, 1, Duration.ofSeconds(10), Duration.ofMinutes(1));
        ConnectionPool.Connection failed = pool.tryAcquire().orElseThrow();
        pool.release(failed, false);

        ConnectionPool.Connection replacement = pool.tryAcquire().orElseThrow();
        assertThat(replacement.id()).isNotEqualTo(failed.id());
        pool.release(replacement, true);
        clock.advance(Duration.ofSeconds(10));

        assertThat(pool.tryAcquire().orElseThrow().id()).isNotEqualTo(replacement.id());
    }
}
