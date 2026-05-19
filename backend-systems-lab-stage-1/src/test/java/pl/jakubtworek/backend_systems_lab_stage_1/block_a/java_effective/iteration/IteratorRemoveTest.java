package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IteratorRemoveTest {

    @Test
    void shouldRemoveEvenNumbers() {

        List<Integer> list =
                new ArrayList<>(List.of(1,2,3,4));

        IteratorRemoveExample.removeEven(list);

        assertEquals(List.of(1,3), list);
    }
}