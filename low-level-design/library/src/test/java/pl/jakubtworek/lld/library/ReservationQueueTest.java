package pl.jakubtworek.lld.library;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ArrayList jest celowo wolną wyrocznią pozycji; nie używa rozmiarów poddrzew.
 */
class ReservationQueueTest {

    @Test
    void ranksAndFifoMatchListAfterArbitraryCancellationsAndReinsertions() {
        var queue = new ReservationQueue();
        var model = new ArrayList<MemberId>();
        var random = new Random(4917);
        for (int step = 0; step < 8000; step++) {
            var member = new MemberId("M" + random.nextInt(100));
            if (model.contains(member)) {
                model.remove(member);
                queue.remove(member);
            } else {
                model.add(member);
                queue.add(member);
            }
            assertEquals(model, queue.snapshot(), "krok " + step);
            for (int i = 0; i < 100; i++) {
                var candidate = new MemberId("M" + i);
                assertEquals(model.indexOf(candidate), queue.rank(candidate));
                assertEquals(model.contains(candidate), queue.contains(candidate));
            }
        }
    }

    @Test
    void sequentialAppendsHaveLogarithmicHeightAndCancellationKeepsRanks() {
        var queue = new ReservationQueue();
        for (int i = 0; i < 10000; i++) {
            queue.add(new MemberId("M" + i));
        }
        assertTrue(queue.indexHeight() <= 20, "Posortowane numery nie mogą degenerować AVL");
        for (int i = 0; i < 10000; i += 2) {
            queue.remove(new MemberId("M" + i));
        }
        for (int i = 1; i < 10000; i += 2) {
            assertEquals(i / 2, queue.rank(new MemberId("M" + i)));
        }
        assertTrue(queue.indexHeight() <= 20);
    }
}
