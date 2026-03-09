package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates modern Map API operations introduced in Java 8.
 *
 * These methods allow performing compound operations atomically,
 * especially important when using ConcurrentHashMap.
 */
public class MapComputeExample {

    /**
     * computeIfAbsent
     *
     * If key does not exist → compute value and insert.
     * If key exists → return existing value.
     *
     * Typical usage:
     * cache
     */
    public static String computeIfAbsentExample(Map<Integer, String> map, int key) {

        return map.computeIfAbsent(key, k -> "User-" + k);
    }

    /**
     * compute
     *
     * Always executes the function.
     *
     * Useful for read-modify-write operations.
     */
    public static void computeExample(Map<String, Integer> map, String key) {

        map.compute(key, (k, v) -> {

            if (v == null)
                return 1;

            return v + 1;
        });
    }

    /**
     * merge
     *
     * If key absent → insert value
     * If key present → combine old and new value
     *
     * Common pattern for counters.
     */
    public static void mergeExample(Map<String, Integer> map, String key) {

        map.merge(key, 1, Integer::sum);
    }

    /**
     * Demonstrates typical counter use-case with ConcurrentHashMap.
     */
    public static void incrementCounter(ConcurrentHashMap<String, Integer> map, String key) {

        map.merge(key, 1, Integer::sum);
    }
}