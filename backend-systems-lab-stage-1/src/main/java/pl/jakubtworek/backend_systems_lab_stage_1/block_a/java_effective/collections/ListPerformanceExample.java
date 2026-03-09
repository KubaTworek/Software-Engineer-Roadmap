package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Demonstrates performance differences between ArrayList and LinkedList.
 *
 * ArrayList:
 *  - backed by dynamic array
 *  - fast random access O(1)
 *  - expensive middle insert O(n)
 *
 * LinkedList:
 *  - doubly linked list
 *  - fast insert/remove at ends
 *  - slow random access O(n)
 */
public class ListPerformanceExample {

    public static long testRandomAccess(List<Integer> list, int size) {

        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        long start = System.nanoTime();

        for (int i = 0; i < size; i++) {
            list.get(i);
        }

        return System.nanoTime() - start;
    }

    public static long testInsertBeginning(List<Integer> list, int size) {

        long start = System.nanoTime();

        for (int i = 0; i < size; i++) {
            list.add(0, i);
        }

        return System.nanoTime() - start;
    }

    public static List<Integer> createArrayList() {
        return new ArrayList<>();
    }

    public static List<Integer> createLinkedList() {
        return new LinkedList<>();
    }
}