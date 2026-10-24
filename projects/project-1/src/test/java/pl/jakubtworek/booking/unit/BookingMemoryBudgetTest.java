package pl.jakubtworek.booking.unit;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.booking.service.profiling.BookingMemoryBudget;
import static org.junit.jupiter.api.Assertions.*;

class BookingMemoryBudgetTest {
    @Test void freeHeapDoesNotProveThatReservationReplicaFitsContainerLimit() {
        assertFalse(new BookingMemoryBudget(512, 480, 100).fits());
        assertTrue(new BookingMemoryBudget(512, 350, 100).fits());
        assertFalse(new BookingMemoryBudget(Long.MAX_VALUE, Long.MAX_VALUE, 1).fits());
    }
}
