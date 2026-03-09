package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * HashSet:
 *  - hash table
 *  - no ordering
 *
 * TreeSet:
 *  - sorted set
 *  - red-black tree
 */
public class SetExample {

    public static Set<Integer> createHashSet() {
        return new HashSet<>();
    }

    public static Set<Integer> createTreeSet() {
        return new TreeSet<>();
    }
}