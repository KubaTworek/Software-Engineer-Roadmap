package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Simple example showing two Map implementations that differ
 * mainly in how keys are stored and retrieved.
 */
public class MapExample {

    public static Map<Integer, String> createHashMap() {
        // Creates a HashMap instance.
        // Elements are stored using a hash table structure.
        // The iteration order of keys is NOT guaranteed.
        return new HashMap<>();
    }

    public static Map<Integer, String> createTreeMap() {
        // Creates a TreeMap instance.
        // Keys are stored in a sorted structure (red-black tree).
        // Iteration over the map will return entries ordered by key.
        return new TreeMap<>();
    }

    public static void populate(Map<Integer, String> map, int size) {

        // Inserts "size" elements into the provided map.
        // Keys:   0, 1, 2, ... size-1
        // Values: "Value-0", "Value-1", ...
        for (int i = 0; i < size; i++) {

            // put() either inserts a new entry or replaces
            // the value if the key already exists.
            map.put(i, "Value-" + i);
        }
    }
}