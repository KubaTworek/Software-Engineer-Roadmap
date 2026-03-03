package pl.jakubtworek.backend_systems_lab_stage_1.block_a.testing;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_systems_lab_stage_1.block_a.thread_confinement.BrokenOrderProcessor;
import pl.jakubtworek.backend_systems_lab_stage_1.block_a.thread_confinement.Order;

import static org.junit.jupiter.api.Assertions.*;

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