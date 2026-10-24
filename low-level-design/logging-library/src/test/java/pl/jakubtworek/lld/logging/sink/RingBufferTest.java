package pl.jakubtworek.lld.logging.sink;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Deque modeluje kolejność niezależnie od modularnych indeksów pierścienia.
 */
class RingBufferTest {

    @Test
    void ringMatchesDequeAcrossThousandsOfWrapsAndClears() throws Exception {
        for (int capacity : new int[] { 1, 2, 7, 32 }) {
            var sink = new MemoryLogSink(capacity, OverflowPolicy.DROP_OLDEST);
            var model = new ArrayDeque<String>();
            for (int i = 0; i < 10000; i++) {
                if (i % 997 == 0) {
                    sink.clear();
                    model.clear();
                }
                String value = "r" + i;
                if (model.size() == capacity) {
                    model.removeFirst();
                }
                model.addLast(value);
                sink.write(value);
                assertEquals(List.copyOf(model), sink.records());
            }
        }
    }
}
