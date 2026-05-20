package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.collections;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentMapTest {

    @Test
    void concurrentHashMapShouldHandleConcurrency() throws InterruptedException {

        Map<Integer, Integer> map =
                ConcurrentMapExample.createConcurrentMap();

        int threads = 16;
        int incrementsPerThread = 100_000;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {

            new Thread(() -> {
                try {
                    start.await();

                    for (int j = 0; j < incrementsPerThread; j++) {
                        map.merge(1, 1, Integer::sum);
                    }

                } catch (InterruptedException ignored) {}

                done.countDown();
            }).start();
        }

        start.countDown();
        done.await();

        int expected = threads * incrementsPerThread;

        assertEquals(expected, map.get(1));
    }

    @Test
    void hashMapShouldLoseUpdatesUnderConcurrency() throws InterruptedException {

        Map<Integer, Integer> map = ConcurrentMapExample.createHashMap();

        int threads = 16;
        int incrementsPerThread = 100_000;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {

            new Thread(() -> {
                try {
                    start.await();

                    for (int j = 0; j < incrementsPerThread; j++) {
                        ConcurrentMapExample.increment(map, 1);
                    }

                } catch (InterruptedException ignored) {}

                done.countDown();
            }).start();
        }

        start.countDown();
        done.await();

        int expected = threads * incrementsPerThread;
        int actual = map.getOrDefault(1, 0);

        System.out.println("Expected: " + expected);
        System.out.println("Actual: " + actual);

        assertTrue(actual < expected,
                "HashMap lost updates due to race condition");
    }
}