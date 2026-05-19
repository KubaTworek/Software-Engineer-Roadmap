package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpliteratorTest {

    @Test
    void shouldSumNumbersUsingSpliterator() {

        int[] numbers = {1,2,3,4};

        int sum = SpliteratorExample.sum(numbers);

        assertEquals(10, sum);
    }
}