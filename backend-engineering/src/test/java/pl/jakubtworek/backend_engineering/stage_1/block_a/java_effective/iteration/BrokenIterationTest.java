package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.iteration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrokenIterationTest {

    @Test
    void shouldThrowConcurrentModificationException() {

        List<Integer> list =
                new ArrayList<>(List.of(1,2,3));

        assertThrows(
                Exception.class,
                () -> BrokenIterationExample.brokenRemove(list)
        );
    }
}