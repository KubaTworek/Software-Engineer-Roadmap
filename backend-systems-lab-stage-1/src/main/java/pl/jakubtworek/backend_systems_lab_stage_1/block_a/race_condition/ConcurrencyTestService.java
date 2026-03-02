package pl.jakubtworek.backend_systems_lab_stage_1.block_a.race_condition;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrencyTestService {

    public Result runTest(TicketStore store, int threads) throws InterruptedException {

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    ready.countDown();
                    start.await();
                    store.buy();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        return new Result(
                store.getInitial(),
                store.getAvailable(),
                threads,
                store.name()
        );
    }
}