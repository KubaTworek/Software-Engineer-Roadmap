package pl.jakubtworek.lld.elevators;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Kontrakt FIFO porównujemy z ArrayDeque, zachowując także stare korzenie.
 */
class StopQueueTest {

    @Test
    void persistentQueueMatchesDequeAcrossReversalsAndExtremeFloorNumbers() {
        var queue = StopQueue.empty();
        var model = new ArrayDeque<Integer>();
        var old = new ArrayList<StopQueue>();
        var values = new ArrayList<List<Integer>>();
        var random = new Random(8191);
        for (int step = 0; step < 6000; step++) {
            if (step % 100 == 0) {
                old.add(queue);
                values.add(List.copyOf(model));
            }
            int candidate = step == 0 ? Integer.MIN_VALUE : step == 1 ? Integer.MAX_VALUE : random.nextInt(2000) - 1000;
            if (!model.isEmpty() && random.nextBoolean()) {
                assertEquals(model.removeFirst(), queue.getFirst());
                queue = queue.tail();
            } else if (!model.contains(candidate)) {
                model.addLast(candidate);
                queue = queue.append(candidate);
            }
            assertEquals(List.copyOf(model), queue);
            assertEquals(model.contains(candidate), queue.contains(candidate));
            assertEquals(model.contains(Integer.MIN_VALUE), queue.contains(Integer.MIN_VALUE));
            assertEquals(model.contains(Integer.MAX_VALUE), queue.contains(Integer.MAX_VALUE));
        }
        for (int i = 0; i < old.size(); i++) {
            assertEquals(values.get(i), old.get(i));
        }
    }
}
