package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates difference between HashMap and ConcurrentHashMap.
 *
 * HashMap:
 *  - not thread-safe
 *
 * ConcurrentHashMap:
 *  - thread-safe
 *  - lock striping
 *  - CAS operations
 */
public class ConcurrentMapExample {

    public static Map<Integer, Integer> createHashMap() {
        return new HashMap<>();
    }

    public static Map<Integer, Integer> createConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    public static void increment(Map<Integer, Integer> map, int key) {

        map.put(key, map.getOrDefault(key, 0) + 1);
    }
}