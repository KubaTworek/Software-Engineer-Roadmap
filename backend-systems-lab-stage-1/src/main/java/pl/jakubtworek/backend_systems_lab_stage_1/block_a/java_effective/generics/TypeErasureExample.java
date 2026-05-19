package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import java.util.List;

/**
 * Example showing type erasure in Java generics.
 *
 * At compile time the compiler checks type parameters (String, Integer),
 * but after compilation the JVM sees only the raw type List.
 */
public class TypeErasureExample {

    public static boolean compareLists(List<String> a, List<Integer> b) {

        // At runtime both objects are instances of the same class: java.util.ArrayList (or other List implementation)
        // Information about generic types (String, Integer) is removed during compilation.
        // Therefore this comparison returns true if both lists are created from the same implementation.
        return a.getClass() == b.getClass();
    }
}