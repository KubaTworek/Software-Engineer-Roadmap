package pl.jakubtworek.lld.cache.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndexedStructuresTest {

    private record Deadline(int key, long deadline) {
    }

    /**
     * Mapa ze skanem nie odtwarza przesiewania kopca; sprawdza minimum i ścisły limit pamięci.
     */
    @Test
    void heapMatchesIndependentMapUnderReplacementRemovalAndPolling() {
        var heap = new IndexedDeadlineHeap<Integer, Deadline>(Comparator.comparingLong(Deadline::deadline),
                Deadline::key);
        var model = new HashMap<Integer, Deadline>();
        var random = new Random(140017);
        for (int step = 0; step < 20000; step++) {
            int key = random.nextInt(300);
            switch(random.nextInt(3)) {
                case 0 ->
                    {
                        var value = new Deadline(key, random.nextInt(10000));
                        model.put(key, value);
                        heap.add(value);
                    }
                case 1 ->
                    {
                        model.remove(key);
                        heap.removeKey(key);
                    }
                default ->
                    {
                        if (!model.isEmpty()) {
                            long minimum = model.values().stream().mapToLong(Deadline::deadline).min().orElseThrow();
                            var value = heap.poll();
                            assertEquals(minimum, value.deadline());
                            assertEquals(value, model.remove(value.key()));
                        }
                    }
            }
            assertEquals(model.size(), heap.size());
            if (!model.isEmpty()) {
                assertEquals(model.values().stream().mapToLong(Deadline::deadline).min().orElseThrow(), heap.peek()
                        .deadline());
            }
        }
    }

    /**
     * Niezależna wyrocznia LFU wybiera minimum skanem częstotliwości i czasu dotknięcia.
     */
    @Test
    void frequencyBucketsMatchScanningOracle() {
        var index = new FrequencyIndex<Integer>();
        var counts = new HashMap<Integer, Long>();
        var age = new HashMap<Integer, Integer>();
        var random = new Random(141007);
        for (int step = 0; step < 10000; step++) {
            int key = random.nextInt(200);
            if (random.nextInt(4) == 0) {
                counts.remove(key);
                age.remove(key);
                index.remove(key);
            } else {
                if (counts.containsKey(key)) {
                    index.touch(key);
                    counts.put(key, counts.get(key) + 1);
                } else {
                    index.add(key);
                    counts.put(key, 1L);
                }
                age.put(key, step);
            }
            if (!counts.isEmpty()) {
                int victim = counts.keySet().stream().min(Comparator.<Integer>comparingLong(counts::get)
                        .thenComparingInt(age::get)).orElseThrow();
                assertEquals(victim, index.victim());
            }
        }
    }
}
