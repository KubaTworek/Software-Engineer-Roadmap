package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import java.util.List;

/**
 * Example demonstrating heap pollution when generics are combined with arrays.
 *
 * Varargs of generic types are internally represented as arrays,
 * which allows inserting values of incorrect types.
 */
public class HeapPollutionExample {

    public static void dangerous(List<String>... lists) {

        // Varargs parameter is internally treated as an array: List<String>[]
        // Here we assign it to Object[] because arrays are covariant in Java
        Object[] array = lists;

        // We insert a List<Integer> into the array that is supposed to contain List<String>
        // This breaks the generic type contract -> heap pollution
        array[0] = List.of(42);

        // The compiler assumes lists[0] contains List<String>
        // but in reality it now contains List<Integer>
        // When trying to read the element as String, a runtime exception occurs
        String value = lists[0].get(0); // ClassCastException
    }
}