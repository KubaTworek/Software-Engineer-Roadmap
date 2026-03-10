package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Example showing two different Queue implementations.
 */
public class QueueExample {

    public static Queue<Integer> createArrayDeque() {
        // Creates a queue backed by a resizable array.
        // Elements will typically be processed in FIFO order
        // (first element added is the first removed).
        return new ArrayDeque<>();
    }

    public static Queue<Integer> createPriorityQueue() {
        // Creates a queue that orders elements according to priority.
        // Elements are internally organized as a heap structure.
        // poll() / remove() will return the smallest element (natural ordering).
        return new PriorityQueue<>();
    }
}