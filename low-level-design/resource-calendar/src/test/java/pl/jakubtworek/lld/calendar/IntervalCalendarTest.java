package pl.jakubtworek.lld.calendar;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntervalCalendarTest {

    private record Block(int id, int start, int end) {
    }

    /**
     * Model filtruje całą mapę, bez poprzednika, iteratora zakresu i drugiego indeksu.
     */
    @Test
    void sharedCalendarMatchesFullScanAfterRandomReplacementsAndRemovals() {
        var calendar = new IntervalCalendar<Integer, Integer, Block>();
        var model = new HashMap<Integer, Block>();
        var random = new Random(420024);
        for (int step = 0; step < 10000; step++) {
            int id = random.nextInt(100), start = random.nextInt(1000), end = start + 1 + random.nextInt(80);
            int operation = random.nextInt(3);
            var expected = model.values().stream().filter(b -> b.id() != id && b.start() < end && start < b.end())
                    .sorted(Comparator.comparingInt(Block::start)).toList();
            if (operation == 0) {
                calendar.remove(id);
                model.remove(id);
            } else if (operation == 1) {
                var block = new Block(id, start, end);
                if (expected.isEmpty()) {
                    calendar.put(id, start, end, block);
                    model.put(id, block);
                } else {
                    assertThrows(IllegalArgumentException.class, () -> calendar.put(id, start, end, block));
                }
            } else {
                assertEquals(expected, calendar.conflicts(start, end, id));
                assertEquals(expected.stream().findFirst(), calendar.firstConflict(start, end, id));
            }
            assertEquals(model.size(), calendar.size());
        }
    }

    @Test
    void halfOpenBoundariesAndExcludedIdentityWorkForDatesToo() {
        var calendar = new IntervalCalendar<LocalDate, String, String>();
        LocalDate day = LocalDate.of(2026, 10, 24);
        calendar.put("a", day, day.plusDays(2), "A");
        calendar.put("b", day.plusDays(2), day.plusDays(4), "B");
        assertEquals(List.of("A", "B"), calendar.conflicts(day, day.plusDays(4), null));
        assertTrue(calendar.firstConflict(day, day.plusDays(2), "a").isEmpty());
        assertEquals(Optional.of("B"), calendar.firstConflict(day, day.plusDays(3), "a"));
        var old = calendar.conflicts(day, day.plusDays(4), null);
        assertThrows(IllegalArgumentException.class, () -> calendar.put("a", day, day.plusDays(3), "changed"));
        assertEquals(old, calendar.conflicts(day, day.plusDays(4), null));
        assertThrows(IllegalArgumentException.class, () -> calendar.conflicts(day, day, null));
        assertThrows(UnsupportedOperationException.class, old::clear);
    }
}
