package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import java.util.List;

/**
 * Generics in Java exist only at compile time.
 *
 * After compilation:
 *
 * List<String>
 * List<Integer>
 *
 * both become:
 *
 * List
 */
public class TypeErasureExample {

    public static boolean compareLists(List<String> a, List<Integer> b) {

        return a.getClass() == b.getClass();
    }
}