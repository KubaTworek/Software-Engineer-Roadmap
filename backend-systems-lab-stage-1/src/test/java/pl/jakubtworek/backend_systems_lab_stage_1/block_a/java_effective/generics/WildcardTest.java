package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class WildcardTest {

    @Test
    void shouldSumNumbers() {

        List<Integer> numbers = List.of(1, 2, 3);

        double sum = WildcardExample.sum(numbers);

        assertEquals(6, sum);
    }

    @Test
    void shouldAddNumbersToList() {

        List<Number> list = new ArrayList<>();

        WildcardExample.addNumbers(list);

        assertEquals(2, list.size());
    }
}