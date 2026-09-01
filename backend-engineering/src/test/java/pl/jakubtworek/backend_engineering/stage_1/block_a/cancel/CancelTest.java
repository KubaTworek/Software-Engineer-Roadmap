package pl.jakubtworek.backend_engineering.stage_1.block_a.cancel;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancelTest {

    @Test
    void cancellableTaskShouldStopAfterInterrupt() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Thread worker = new Thread(new CancellableTask(), "cooperative-task-test");
            worker.start();

            awaitStarted(worker);
            worker.interrupt();
            worker.join(1_000);

            assertFalse(worker.isAlive(), "Task respecting interruption should terminate");
        });
    }

    @Test
    void taskIgnoringInterruptShouldRemainAlive() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Thread worker = new Thread(new BadCancellableTask(), "broken-task-test");

            // The example is intentionally impossible to stop. A daemon thread
            // demonstrates the defect without preventing the test JVM from exiting.
            worker.setDaemon(true);
            worker.start();

            awaitStarted(worker);
            worker.interrupt();
            worker.join(250);

            assertTrue(worker.isAlive(), "Task swallowing InterruptedException keeps running");
        });
    }

    private static void awaitStarted(Thread worker) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (worker.getState() == Thread.State.NEW && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertFalse(worker.getState() == Thread.State.NEW, "Worker should start within one second");
    }
}
