package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Example presenting two Set implementations that differ
 * mainly in how elements are stored and iterated.
 */
public class SetExample {

    public static Set<Integer> createHashSet() {
        // Creates a HashSet instance.
        // Elements are stored using a hashing mechanism.
        // The order of elements during iteration is not guaranteed.
        return new HashSet<>();
    }

    public static Set<Integer> createTreeSet() {
        // Creates a TreeSet instance.
        // Elements are stored in a sorted structure (red-black tree).
        // Iterating over the set will always return elements in sorted order.
        return new TreeSet<>();
    }
}