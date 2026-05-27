package pl.jakubtworek.backend_engineering.stage_1.block_a.cancel;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_1.block_a.cancel.BadCancellableTask;
import pl.jakubtworek.backend_engineering.stage_1.block_a.cancel.CancellableTask;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CancelTest {

    @Test
    void cancellableTask_shouldStopAfterCancel() throws Exception {

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> future = exec.submit(new CancellableTask());

        Thread.sleep(200);

        future.cancel(true); // sends interrupt

        exec.shutdown();
        boolean terminated = exec.awaitTermination(2, TimeUnit.SECONDS);

        assertTrue(terminated,
                "Executor should terminate when task respects interrupt");
    }

    @Test
    void badTask_shouldNotStopAfterCancel() throws Exception {

        ExecutorService exec = Executors.newSingleThreadExecutor();
        Future<?> future = exec.submit(new BadCancellableTask());

        Thread.sleep(200);

        future.cancel(true);

        exec.shutdown();
        boolean terminated = exec.awaitTermination(1, TimeUnit.SECONDS);

        assertFalse(terminated,
                "Executor should NOT terminate when task ignores interrupt");

        exec.shutdownNow(); // force cleanup for test
    }
}