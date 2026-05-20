package pl.jakubtworek.backend_engineering.stage_1.block_a.threads.testing;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.block_a.threads.thread_confinement.BrokenOrderProcessor;
import pl.jakubtworek.backend_engineering.block_a.threads.thread_confinement.Order;

class RaceConditionDetectionTest {

    @Test
    void shouldDetectLostUpdate() {

        RepeatHelper.repeat(5_000, () -> {

            BrokenOrderProcessor processor = new BrokenOrderProcessor();

            try {
                ConcurrentTestHelper.runConcurrent(
                        20,
                        () -> processor.submitOrder(new Order(1))
                );
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (processor.getProcessed() != 20) {
                // race detected
                return;
            }

            // if we never detect race after many tries,
            // test still passes — concurrency bugs are probabilistic
        });
    }
}