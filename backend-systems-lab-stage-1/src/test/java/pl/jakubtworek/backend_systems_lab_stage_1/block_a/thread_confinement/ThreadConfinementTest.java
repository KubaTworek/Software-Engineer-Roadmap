package pl.jakubtworek.backend_systems_lab_stage_1.block_a.thread_confinement;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ThreadConfinementTest {

    // ================================
    // 1️⃣ Broken version (race expected)
    // ================================

    @Test
    void brokenProcessor_shouldLoseUpdates() throws InterruptedException {

        BrokenOrderProcessor processor = new BrokenOrderProcessor();

        int threads = 50;
        int perThread = 1_000;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        processor.submitOrder(new Order(j));
                    }
                } catch (InterruptedException ignored) {}
                done.countDown();
            }).start();
        }

        start.countDown();
        done.await();

        int expected = threads * perThread;
        int actual = processor.getProcessed();

        assertTrue(actual < expected,
                "Broken processor should lose updates under concurrency");
    }

    // ================================
    // 2️⃣ Synchronized version
    // ================================

    @Test
    void synchronizedProcessor_shouldProcessAllOrders() throws InterruptedException {

        SynchronizedOrderProcessor processor = new SynchronizedOrderProcessor();

        int threads = 50;
        int perThread = 1_000;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        processor.submitOrder(new Order(j));
                    }
                } catch (InterruptedException ignored) {}
                done.countDown();
            }).start();
        }

        start.countDown();
        done.await();

        assertEquals(threads * perThread, processor.getProcessed());
    }

    // ================================
    // 3️⃣ Single-thread confinement
    // ================================

    @Test
    void confinedProcessor_shouldProcessAllOrders() throws Exception {

        ConfinedOrderProcessor processor =
                new ConfinedOrderProcessor();

        int threads = 50;
        int perThread = 1_000;

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        List<Future<?>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        futures.add(processor.submitOrder(new Order(j)));
                    }
                } catch (InterruptedException ignored) {}
                done.countDown();
            }).start();
        }

        start.countDown();
        done.await();

        // wait for executor tasks
        for (Future<?> f : futures) {
            f.get();
        }

        assertEquals(threads * perThread, processor.getProcessed());

        processor.shutdown();
    }

    // ================================
    // 4️⃣ ThreadLocal isolation
    // ================================

    @Test
    void threadLocal_shouldIsolateStatePerThread() throws InterruptedException {

        ThreadLocalExample example = new ThreadLocalExample();

        int threads = 10;
        CountDownLatch done = new CountDownLatch(threads);

        List<Integer> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    example.increment();
                }
                results.add(example.get());
                done.countDown();
            }).start();
        }

        done.await();

        for (Integer value : results) {
            assertEquals(100, value,
                    "Each thread should have its own independent counter");
        }
    }
}