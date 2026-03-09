package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import java.util.List;

/**
 * Heap pollution example.
 *
 * Mixing generic types with arrays
 * can break type safety.
 */
public class HeapPollutionExample {

    public static void dangerous(List<String>... lists) {

        Object[] array = lists;

        array[0] = List.of(42); // heap pollution

        String value = lists[0].get(0); // ClassCastException
    }
}