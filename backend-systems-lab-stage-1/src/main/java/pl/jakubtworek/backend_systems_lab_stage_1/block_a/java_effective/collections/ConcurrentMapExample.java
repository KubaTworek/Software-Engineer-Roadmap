package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstrates difference between HashMap and ConcurrentHashMap
 * in the context of simple increment operations on a shared map.
 */
public class ConcurrentMapExample {

    public static Map<Integer, Integer> createHashMap() {
        // Creates a standard HashMap.
        // If this map is accessed by multiple threads at the same time,
        // race conditions may occur because HashMap does not provide synchronization.
        return new HashMap<>();
    }

    public static Map<Integer, Integer> createConcurrentMap() {
        // Creates a thread-safe map implementation.
        // ConcurrentHashMap allows multiple threads to read and update
        // different parts of the map concurrently without external synchronization.
        return new ConcurrentHashMap<>();
    }

    public static void increment(Map<Integer, Integer> map, int key) {

        // Reads the current value associated with the key.
        // If the key does not exist, getOrDefault returns 0.
        int currentValue = map.getOrDefault(key, 0);

        // Stores incremented value back into the map.
        // WARNING: This is not an atomic operation because it consists of:
        // 1. read (getOrDefault)
        // 2. computation (+1)
        // 3. write (put)
        //
        // When multiple threads execute this method concurrently,
        // updates may be lost even if the map is ConcurrentHashMap.
        map.put(key, currentValue + 1);
    }
}