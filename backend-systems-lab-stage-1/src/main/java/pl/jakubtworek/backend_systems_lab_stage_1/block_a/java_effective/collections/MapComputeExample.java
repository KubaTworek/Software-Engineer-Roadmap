package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Examples of Java 8 Map methods used to perform
 * common map updates without manual get/put logic.
 */
public class MapComputeExample {

    /**
     * If the key is missing, a value is created using the lambda
     * and inserted into the map.
     *
     * If the key already exists, the stored value is returned
     * and the lambda is NOT executed.
     */
    public static String computeIfAbsentExample(Map<Integer, String> map, int key) {

        // If key is absent -> inserts "User-{key}"
        // If key exists -> returns existing value without modification
        return map.computeIfAbsent(key, k -> "User-" + k);
    }

    /**
     * Recomputes value for the key every time the method is called.
     *
     * The lambda receives:
     *  - k -> the key
     *  - v -> current value (may be null if key does not exist)
     */
    public static void computeExample(Map<String, Integer> map, String key) {

        map.compute(key, (k, v) -> {

            // When key is not present, v == null
            // so we initialize the counter
            if (v == null)
                return 1;

            // Otherwise increment existing value
            return v + 1;
        });
    }

    /**
     * Inserts value if key does not exist.
     * Otherwise combines existing value with the provided one.
     */
    public static void mergeExample(Map<String, Integer> map, String key) {

        // If key absent -> put(key, 1)
        // If key present -> newValue = Integer.sum(oldValue, 1)
        map.merge(key, 1, Integer::sum);
    }

    /**
     * Typical counter pattern used with ConcurrentHashMap.
     *
     * merge() ensures the update happens safely without
     * separate get() and put() operations.
     */
    public static void incrementCounter(ConcurrentHashMap<String, Integer> map, String key) {

        // Increment counter stored under "key"
        // If key is new -> counter becomes 1
        // Otherwise -> increment existing value
        map.merge(key, 1, Integer::sum);
    }
}