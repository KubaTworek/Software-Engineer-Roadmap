package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.executor_service;

import java.util.concurrent.*;

public class ExecutorConfigurations {

    /**
     * Cached thread pool:
     * - Unbounded thread growth
     * - No queue (SynchronousQueue)
     * - Good for short-lived async tasks
     * - Risk: thread explosion
     */
    public static ExecutorService cachedPool() {
        return Executors.newCachedThreadPool();
    }

    /**
     * Fixed thread pool:
     * - Fixed number of workers
     * - Unbounded LinkedBlockingQueue
     * - Risk: unbounded memory growth (OOM)
     */
    public static ExecutorService fixedPool(int size) {
        return Executors.newFixedThreadPool(size);
    }

    /**
     * Bounded queue + AbortPolicy
     * - Explicit backpressure
     * - RejectedExecutionException when overloaded
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
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Bounded queue + CallerRunsPolicy
     * - Backpressure mechanism
     * - Caller thread executes task when pool saturated
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
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}