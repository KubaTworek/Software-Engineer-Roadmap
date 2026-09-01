package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.overload;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed workers plus a bounded queue; excess work is shed instead of growing without limit. */
public final class BoundedRequestExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger shed = new AtomicInteger();

    public BoundedRequestExecutor(int workers, int queueCapacity) {
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        executor = new ThreadPoolExecutor(
                workers,
                workers,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
        try {
            Future<T> future = executor.submit(task);
            accepted.incrementAndGet();
            return future;
        } catch (RejectedExecutionException exception) {
            shed.incrementAndGet();
            throw new LoadShedException("request rejected because workers and queue are full", exception);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                accepted.get(),
                shed.get(),
                executor.getActiveCount(),
                executor.getQueue().size()
        );
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public record Snapshot(int accepted, int shed, int active, int queued) {
    }
}
