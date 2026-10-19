package pl.jakubtworek.backend.reservation.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReservationEntityTest {
    @Test
    void releasesCapacityOnlyForFirstTerminalTransition() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var reservation = ReservationEntity.pending(UUID.randomUUID(), "user", 2, now);
        assertThat(reservation.cancel(now.plusSeconds(1))).isTrue();
        assertThat(reservation.cancel(now.plusSeconds(2))).isFalse();
    }

    @Test
    void expiryBoundaryUsesInjectedTime() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var reservation = ReservationEntity.pending(UUID.randomUUID(), "user", 1, now);
        assertThat(reservation.isExpired(now.plusSeconds(15 * 60))).isFalse();
        assertThat(reservation.isExpired(now.plusSeconds(15 * 60 + 1))).isTrue();
    }
}
