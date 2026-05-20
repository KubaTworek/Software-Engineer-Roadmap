package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.generics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundedGenericsTest {

    @Test
    void shouldAcceptNumberTypes() {

        NumberBox<Integer> box = new NumberBox<>(10);

        assertEquals(10.0, box.doubleValue());
    }
}