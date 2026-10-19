package pl.jakubtworek.backend_engineering.stage_1.block_a.testing;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaceConditionDetectionTest {

    @Test
    void shouldReproduceLostUpdateDeterministically() throws InterruptedException {
        CoordinatedBrokenCounter counter = new CoordinatedBrokenCounter(2);

        ConcurrentTestHelper.runConcurrent(2, counter::increment);

        assertEquals(1, counter.value(),
                "Both threads read zero before either writes, so one update is lost");
    }

    @Test
    void atomicCounterShouldPreserveBothUpdates() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();

        ConcurrentTestHelper.runConcurrent(2, counter::incrementAndGet);

        assertEquals(2, counter.get());
    }

    /**
     * The barrier deliberately pauses both threads after the read and before
     * the write. This forces the read-modify-write interleaving responsible
     * for a lost update instead of hoping the scheduler happens to produce it.
     */
    private static final class CoordinatedBrokenCounter {
        private final CyclicBarrier bothThreadsHaveRead;
        private int value;

        private CoordinatedBrokenCounter(int participants) {
            this.bothThreadsHaveRead = new CyclicBarrier(participants);
        }

        private void increment() {
            int snapshot = value;
            awaitBarrier();
            value = snapshot + 1;
        }

        private int value() {
            return value;
        }

        private void awaitBarrier() {
            try {
                bothThreadsHaveRead.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Worker interrupted while awaiting test barrier", exception);
            } catch (BrokenBarrierException exception) {
                throw new IllegalStateException("Test barrier was broken", exception);
            }
        }
    }
}
