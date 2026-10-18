package pl.jakubtworek.booking.unit;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.entity.CapacityPool;
import pl.jakubtworek.booking.entity.Event;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dowodzi lokalnych granic capacity niezależnie od mechanizmu lockowania. */
class CapacityPoolInvariantTest {

    @Test
    void capacityMovesOnlyInsideZeroAndConfiguredTotal() {
        Event event = event();
        CapacityPool pool = new CapacityPool(event, 2);

        assertThat(pool.getId()).isNotNull();
        assertThat(pool.getEvent()).isSameAs(event);
        assertThat(pool.getTotalCapacity()).isEqualTo(2);
        assertThat(pool.getAvailableCapacity()).isEqualTo(2);
        assertThat(pool.getVersion()).isZero();

        pool.reserveOne();
        pool.reserveOne();
        assertThat(pool.getAvailableCapacity()).isZero();
        assertThatThrownBy(pool::reserveOne).isInstanceOf(IllegalStateException.class);

        pool.releaseOne();
        pool.releaseOne();
        assertThat(pool.getAvailableCapacity()).isEqualTo(2);
        assertThatThrownBy(pool::releaseOne).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonPositiveTotalCapacity() {
        assertThatThrownBy(() -> new CapacityPool(event(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapacityPool(event(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Event event() {
        return new Event("Capacity event", "Warsaw", "test", OffsetDateTime.parse("2027-01-01T10:00:00Z"));
    }
}
