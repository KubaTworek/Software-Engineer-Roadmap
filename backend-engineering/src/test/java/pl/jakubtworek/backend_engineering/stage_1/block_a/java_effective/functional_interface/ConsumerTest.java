package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.functional_interface;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerTest {

    @Test
    void consumerShouldConsumeValue() {

        AtomicReference<String> holder =
                new AtomicReference<>();

        ConsumerExample.consume(
                "test",
                holder::set
        );

        assertEquals("test", holder.get());
    }
}