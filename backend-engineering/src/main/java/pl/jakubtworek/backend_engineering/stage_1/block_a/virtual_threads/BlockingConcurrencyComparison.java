package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compares how many blocking tasks can start without treating elapsed time as
 * a correctness assertion. Virtual threads make waiting cheap; they do not
 * make the downstream operation itself faster.
 */
public final class BlockingConcurrencyComparison {

    public Comparison compare(int tasks, int platformParallelism, Duration startupTimeout)
            throws InterruptedException {
        if (tasks <= 0) {
            throw new IllegalArgumentException("tasks must be positive");
        }
        if (platformParallelism <= 0 || platformParallelism > tasks) {
            throw new IllegalArgumentException("platformParallelism must be between 1 and tasks");
        }
        if (startupTimeout == null || startupTimeout.isZero() || startupTimeout.isNegative()) {
            throw new IllegalArgumentException("startupTimeout must be positive");
        }

        ConcurrencyObservation platform = observe(
                Executors.newFixedThreadPool(platformParallelism),
                tasks,
                platformParallelism,
                startupTimeout
        );
        ConcurrencyObservation virtual = observe(
                Executors.newVirtualThreadPerTaskExecutor(),
                tasks,
                tasks,
                startupTimeout
        );
        return new Comparison(platform, virtual);
    }

    private static ConcurrencyObservation observe(
            ExecutorService executor,
            int tasks,
            int expectedStartsBeforeRelease,
            Duration startupTimeout
    ) throws InterruptedException {
        CountDownLatch expectedTasksStarted = new CountDownLatch(expectedStartsBeforeRelease);
        CountDownLatch releaseBlockingCall = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicBoolean everyStartedTaskWasVirtual = new AtomicBoolean(true);

        try {
            for (int task = 0; task < tasks; task++) {
                executor.submit(() -> {
                    if (!Thread.currentThread().isVirtual()) {
                        everyStartedTaskWasVirtual.set(false);
                    }
                    started.incrementAndGet();
                    int nowActive = active.incrementAndGet();
                    maximumActive.accumulateAndGet(nowActive, Math::max);
                    expectedTasksStarted.countDown();
                    try {
                        releaseBlockingCall.await();
                    } finally {
                        active.decrementAndGet();
                    }
                    return null;
                });
            }

            if (!expectedTasksStarted.await(startupTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("tasks did not start within " + startupTimeout);
            }

            return new ConcurrencyObservation(
                    tasks,
                    started.get(),
                    maximumActive.get(),
                    everyStartedTaskWasVirtual.get()
            );
        } finally {
            releaseBlockingCall.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(startupTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("executor did not terminate within " + startupTimeout);
            }
        }
    }

    public record Comparison(
            ConcurrencyObservation platformThreads,
            ConcurrencyObservation virtualThreads
    ) {
    }
}
