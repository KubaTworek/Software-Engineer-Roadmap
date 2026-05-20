package pl.jakubtworek.backend_engineering.stage_1.block_a.threads.concurrent_hash_map;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentHashMapTest {

    @Test
    void brokenCache_shouldComputeMultipleTimes() throws InterruptedException {

        BrokenCache cache = new BrokenCache();

        runConcurrent(() -> cache.getData("A"), 10);

        assertTrue(cache.getComputeCount() > 1,
                "Broken version should compute multiple times");
    }

    @Test
    void atomicCache_shouldComputeOnce() throws InterruptedException {

        AtomicCache cache = new AtomicCache();

        runConcurrent(() -> cache.getData("A"), 10);

        assertEquals(1, cache.getComputeCount(),
                "Atomic version should compute once");
    }

    private void runConcurrent(Runnable task, int threads)
            throws InterruptedException {

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    task.run();
                } catch (InterruptedException ignored) {}
                done.countDown();
            }).start();
        }

        start.countDown();
        done.await();
    }
}