package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapComputeTest {

    @Test
    void computeShouldInsertIfMissing() {

        Map<String, Integer> map = new HashMap<>();

        MapComputeExample.computeExample(map, "A");

        assertEquals(1, map.get("A"));
    }

    @Test
    void computeShouldUpdateExistingValue() {

        Map<String, Integer> map = new HashMap<>();

        map.put("A", 1);

        MapComputeExample.computeExample(map, "A");

        assertEquals(2, map.get("A"));
    }

    @Test
    void shouldComputeValueIfKeyMissing() {

        Map<Integer, String> map = new HashMap<>();

        String value =
                MapComputeExample.computeIfAbsentExample(map, 1);

        assertEquals("User-1", value);
        assertEquals(1, map.size());
    }

    @Test
    void shouldNotOverrideExistingValue() {

        Map<Integer, String> map = new HashMap<>();

        map.put(1, "Existing");

        String value =
                MapComputeExample.computeIfAbsentExample(map, 1);

        assertEquals("Existing", value);
    }

    @Test
    void mergeShouldInsertIfKeyMissing() {

        Map<String, Integer> map = new HashMap<>();

        MapComputeExample.mergeExample(map, "A");

        assertEquals(1, map.get("A"));
    }

    @Test
    void mergeShouldCombineValues() {

        Map<String, Integer> map = new HashMap<>();

        map.put("A", 2);

        MapComputeExample.mergeExample(map, "A");

        assertEquals(3, map.get("A"));
    }

    @Test
    void mergeShouldHandleConcurrency() throws InterruptedException {

        ConcurrentHashMap<String, Integer> map =
                new ConcurrentHashMap<>();

        int threads = 10;
        int incrementsPerThread = 10_000;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {

            new Thread(() -> {

                try {
                    start.await();

                    for (int j = 0; j < incrementsPerThread; j++) {
                        MapComputeExample.incrementCounter(map, "counter");
                    }

                } catch (InterruptedException ignored) {}

                done.countDown();

            }).start();
        }

        start.countDown();
        done.await();

        int expected = threads * incrementsPerThread;

        assertEquals(expected, map.get("counter"));
    }
}