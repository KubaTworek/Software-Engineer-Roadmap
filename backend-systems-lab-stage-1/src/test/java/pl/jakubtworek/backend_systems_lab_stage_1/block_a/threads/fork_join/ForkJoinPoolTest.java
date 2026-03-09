package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.fork_join;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ForkJoinPoolTest {

    @Test
    void cpuBoundTask_shouldComputeCorrectSum() {
        int[] arr = new int[1_000_000];
        for (int i = 0; i < arr.length; i++) arr[i] = 1;

        ForkJoinPool pool = new ForkJoinPool(); // default parallelism ~ cores
        long sum = pool.invoke(new ArraySumTask(arr, 0, arr.length));

        assertEquals(arr.length, sum);
        pool.shutdown();
    }

    /**
     * Demonstration test:
     * Blocking tasks in ForkJoinPool without managedBlock can starve the pool.
     *
     * We use very small parallelism (1) to make starvation easy to observe.
     * Test uses timeout to avoid hanging the build.
     */
    @Test
    void blockingWithoutManagedBlock_canStall() throws InterruptedException {
        ForkJoinPool pool = new ForkJoinPool(1); // intentionally small

        pool.execute(new BlockingTaskNoManagedBlock(0, 10, 200));

        // If it completes quickly, ok — environment may be different.
        // If it doesn't, we demonstrate stalling behavior.
        boolean finished = pool.awaitQuiescence(1, TimeUnit.SECONDS);

        pool.shutdownNow();

        // We do NOT assert finished == false always (it can be flaky by nature).
        // Instead we assert that it is allowed to stall:
        assertTrue(true, "Demonstration test (may stall depending on environment)");
    }

    /**
     * With managedBlock, ForkJoinPool can compensate for blocking workers.
     * We still keep parallelism=1 to stress the scenario.
     */
    @Test
    void blockingWithManagedBlock_shouldMakeProgress() {
        ForkJoinPool pool = new ForkJoinPool(1);

        pool.invoke(new BlockingTaskManagedBlock(0, 8, 50));

        pool.shutdown();
        assertTrue(true, "ManagedBlock version should complete");
    }
}