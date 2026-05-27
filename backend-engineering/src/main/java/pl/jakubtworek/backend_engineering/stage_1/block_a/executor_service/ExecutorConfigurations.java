package pl.jakubtworek.backend_engineering.stage_1.block_a.executor_service;

import java.util.concurrent.*;

public class ExecutorConfigurations {

    /**
     * Cached thread pool
     *
     * Mechanism:
     * - Uses a SynchronousQueue (no internal task queue).
     * - Each submitted task is handed directly to a thread.
     * - If no idle thread is available, a new thread is created.
     *
     * Behavior:
     * - Threads that remain idle for 60 seconds are terminated.
     * - Threads are reused when possible.
     *
     * Practical implication:
     * - Works well for many short-lived asynchronous tasks.
     *
     * Risk:
     * - If tasks block (e.g. waiting on I/O), the pool may create
     *   a large number of threads, potentially exhausting resources.
     */
    public static ExecutorService cachedPool() {
        return Executors.newCachedThreadPool();
    }

    /**
     * Fixed thread pool
     *
     * Mechanism:
     * - Maintains a constant number of worker threads.
     * - Uses an unbounded LinkedBlockingQueue to store waiting tasks.
     *
     * Behavior:
     * - No more than 'size' threads will execute tasks concurrently.
     * - Additional tasks are placed in the queue until a worker becomes free.
     *
     * Risk:
     * - If tasks are produced faster than they are consumed,
     *   the queue can grow indefinitely and eventually cause OutOfMemoryError.
     */
    public static ExecutorService fixedPool(int size) {
        return Executors.newFixedThreadPool(size);
    }

    /**
     * ThreadPoolExecutor with bounded queue and AbortPolicy
     *
     * Mechanism:
     * - Tasks are executed by core threads first.
     * - If all core threads are busy, tasks are placed in the queue.
     * - When the queue is full, the pool may grow up to 'max'.
     *
     * Overload behavior:
     * - If both the pool and queue are full, task submission fails
     *   with RejectedExecutionException.
     *
     * Practical implication:
     * - Forces the caller to handle overload explicitly
     *   (fail-fast instead of silently accumulating work).
     */
    public static ThreadPoolExecutor boundedAbortPolicy(
            int core,
            int max,
            int queueCapacity) {

        return new ThreadPoolExecutor(
                core,
                max,
                1,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy() // rejects tasks when overloaded
        );
    }

    /**
     * ThreadPoolExecutor with bounded queue and CallerRunsPolicy
     *
     * Mechanism:
     * - Same execution model as the previous configuration.
     *
     * Overload behavior:
     * - When the pool and queue are full, the submitting thread
     *   executes the task itself.
     *
     * Practical implication:
     * - Introduces natural backpressure by slowing down the producer.
     * - Instead of rejecting work, the system reduces submission rate.
     *
     * Side effect:
     * - The calling thread may experience increased latency
     *   because it executes tasks synchronously.
     */
    public static ThreadPoolExecutor boundedCallerRunsPolicy(
            int core,
            int max,
            int queueCapacity) {

        return new ThreadPoolExecutor(
                core,
                max,
                1,
                TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy() // caller executes task when overloaded
        );
    }
}