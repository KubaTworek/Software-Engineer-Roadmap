package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Demonstrates difference between HashMap and TreeMap.
 *
 * HashMap:
 *  - hash table
 *  - average O(1)
 *  - no ordering
 *
 * TreeMap:
 *  - red-black tree
 *  - O(log n)
 *  - sorted keys
 */
public class MapExample {

    public static Map<Integer, String> createHashMap() {
        return new HashMap<>();
    }

    public static Map<Integer, String> createTreeMap() {
        return new TreeMap<>();
    }

    public static void populate(Map<Integer, String> map, int size) {
        for (int i = 0; i < size; i++) {
            map.put(i, "Value-" + i);
        }
    }
}