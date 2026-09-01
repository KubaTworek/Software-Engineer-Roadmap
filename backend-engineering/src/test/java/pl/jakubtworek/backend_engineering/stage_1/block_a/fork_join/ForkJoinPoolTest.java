package pl.jakubtworek.backend_engineering.stage_1.block_a.fork_join;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForkJoinPoolTest {

    @Test
    void cpuBoundTaskShouldComputeCorrectSum() {
        int[] values = new int[1_000_000];
        Arrays.fill(values, 1);
        ForkJoinPool pool = new ForkJoinPool();
        try {
            assertEquals(values.length, pool.invoke(new ArraySumTask(values, 0, values.length)));
        } finally {
            shutdown(pool);
        }
    }

    @Test
    void shouldRejectInvalidArrayRange() {
        int[] values = new int[10];

        assertThrows(IllegalArgumentException.class,
                () -> new ArraySumTask(values, -1, values.length));
        assertThrows(IllegalArgumentException.class,
                () -> new ArraySumTask(values, 5, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new ArraySumTask(values, 0, values.length + 1));
    }

    @Test
    void unmanagedBlockingExampleShouldFinishAndReleasePool() {
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            Future<?> task = pool.submit(new BlockingTaskNoManagedBlock(0, 3, 5));
            assertDoesNotThrow(() -> task.get(2, TimeUnit.SECONDS));
        } finally {
            shutdown(pool);
        }
    }

    @Test
    void managedBlockingExampleShouldFinishAndReleasePool() {
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            Future<?> task = pool.submit(new BlockingTaskManagedBlock(0, 3, 5));
            assertDoesNotThrow(() -> task.get(2, TimeUnit.SECONDS));
        } finally {
            shutdown(pool);
        }
    }

    @Test
    void blockingTasksShouldValidateConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlockingTaskNoManagedBlock(-1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockingTaskManagedBlock(2, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockingTaskManagedBlock(0, 1, -1));
    }

    private static void shutdown(ForkJoinPool pool) {
        pool.shutdownNow();
        try {
            assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting pool termination", exception);
        }
    }
}
