package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;

/**
 * Niezależna wyrocznia zamienia przedziały na zbiory dni zamiast powielać nierówności.
 */
class RentalPeriodTest {

    @Test
    void all225PairsOfSmallPeriodsAgreeWithDaySetIntersection() {
        // Sześć granic 0..5 daje 15 niepustych przedziałów i 225 par.
        for (int a = 0; a < 5; a++) {
            for (int b = a + 1; b <= 5; b++) {
                for (int c = 0; c < 5; c++) {
                    for (int d = c + 1; d <= 5; d++) {
                        var left = new HashSet<Integer>();
                        for (int day = a; day < b; day++) {
                            left.add(day);
                        }
                        boolean expected = false;
                        for (int day = c; day < d; day++) {
                            expected |= left.contains(day);
                        }
                        assertEquals(expected, period(a, b).overlaps(period(c, d)));
                        assertEquals(expected, period(c, d).overlaps(period(a, b)));
                    }
                }
            }
        }
    }

    @Test
    void adjacencyIsAllowedButContainmentIsConflict() {
        assertFalse(period(0, 2).overlaps(period(2, 4)));
        assertTrue(period(0, 5).overlaps(period(1, 2)));
        assertTrue(period(1, 2).overlaps(period(0, 5)));
        assertThrows(IllegalArgumentException.class, () -> period(1, 1));
        assertThrows(IllegalArgumentException.class, () -> period(2, 1));
        assertThrows(NullPointerException.class, () -> new RentalPeriod(null, TODAY));
    }

    @Test
    void calendarDaysIncludeLeapDayWithoutHourRounding() {
        var leap = new RentalPeriod(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1));
        assertEquals(2, leap.days());
    }
}
