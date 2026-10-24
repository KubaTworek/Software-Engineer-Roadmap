package pl.jakubtworek.lld.meetingrooms;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.meetingrooms.Fixtures.*;

/**
 * Wyczerpująca mała siatka używa zajętych komórek, zamiast powielać wzór dwóch nierówności.
 */
class TimeSlotTest {

    @Test
    void all441SmallIntervalPairsMatchIndependentOccupiedCells() {
        int checked = 0;
        for (int a = 0; a < 6; a++) {
            for (int b = a + 1; b <= 6; b++) {
                for (int c = 0; c < 6; c++) {
                    for (int d = c + 1; d <= 6; d++) {
                        var cells = IntStream.range(a, b).boxed().toList();
                        boolean expected = IntStream.range(c, d).anyMatch(cells::contains);
                        assertEquals(expected, slot(a, b).overlaps(slot(c, d)), a + ":" + b + " / " + c + ":" + d);
                        checked++;
                    }
                }
            }
        }
        assertEquals(441, checked);
    }

    @Test
    void adjacentNanosecondIntervalsDoNotOverlapButOneNanosecondIntersectionDoes() {
        var first = new TimeSlot(NOW, NOW.plusNanos(1));
        var adjacent = new TimeSlot(NOW.plusNanos(1), NOW.plusNanos(2));
        assertFalse(first.overlaps(adjacent));
        assertTrue(new TimeSlot(NOW, NOW.plusNanos(2)).overlaps(adjacent));
    }

    @Test
    void differentOffsetsCanDescribeSameInstantAndZeroOrReversedRangesAreInvalid() {
        assertEquals(OffsetDateTime.parse("2030-01-01T10:00:00+01:00").toInstant(),
                OffsetDateTime.parse("2030-01-01T09:00:00Z").toInstant());
        assertThrows(IllegalArgumentException.class, () -> new TimeSlot(NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new TimeSlot(NOW.plusSeconds(1), NOW));
        assertThrows(NullPointerException.class, () -> new TimeSlot(null, NOW));
        assertThrows(NullPointerException.class, () -> slot(0, 1).overlaps(null));
    }
}
