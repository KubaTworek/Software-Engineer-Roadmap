package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple benchmark comparing two operations on List implementations:
 *  - random access (get by index)
 *  - inserting elements at the beginning of the list
 */
public class ListPerformanceExample {

    public static long testRandomAccess(List<Integer> list, int size) {

        // Fill the list with values 0..size-1
        // This prepares the structure so that random reads can be measured.
        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        // Start measuring time of random access operations
        long start = System.nanoTime();

        // Access elements by index.
        // For ArrayList this is constant time because elements are stored in an array.
        // For LinkedList this requires traversing the list to the requested index.
        for (int i = 0; i < size; i++) {
            list.get(i);
        }

        // Return elapsed time of all get() operations
        return System.nanoTime() - start;
    }

    public static long testInsertBeginning(List<Integer> list, int size) {

        // Start measuring insertion performance
        long start = System.nanoTime();

        // Insert elements always at index 0 (beginning of the list)
        // ArrayList must shift all existing elements to the right each time.
        // LinkedList only updates a few node references.
        for (int i = 0; i < size; i++) {
            list.add(0, i);
        }

        // Return elapsed time of all insert operations
        return System.nanoTime() - start;
    }

    public static List<Integer> createArrayList() {
        // Creates list backed by a resizable array
        return new ArrayList<>();
    }

    public static List<Integer> createLinkedList() {
        // Creates list backed by a doubly linked list structure
        return new LinkedList<>();
    }
}