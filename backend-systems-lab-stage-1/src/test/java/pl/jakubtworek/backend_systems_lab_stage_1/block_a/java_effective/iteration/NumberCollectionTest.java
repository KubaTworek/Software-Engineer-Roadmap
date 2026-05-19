package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberCollectionTest {

    @Test
    void shouldIterateUsingForEach() {

        NumberCollection collection =
                new NumberCollection(new int[]{1,2,3});

        int sum = 0;

        for (int n : collection) {
            sum += n;
        }

        assertEquals(6, sum);
    }
}