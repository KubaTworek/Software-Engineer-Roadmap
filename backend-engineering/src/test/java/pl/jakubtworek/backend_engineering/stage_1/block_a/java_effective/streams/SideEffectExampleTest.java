package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.streams;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SideEffectExampleTest {

    private final SideEffectExample example =
            new SideEffectExample();

    @Test
    void brokenExampleShouldProduceCorrectResultSequentially() {

        List<Integer> numbers = List.of(1, 2, 3);

        List<Integer> result =
                example.brokenExample(numbers);

        assertEquals(List.of(2, 4, 6), result);
    }

    @Test
    void correctExampleShouldProduceCorrectResult() {

        List<Integer> numbers = List.of(1, 2, 3);

        List<Integer> result =
                example.correctExample(numbers);

        assertEquals(List.of(2, 4, 6), result);
    }
}