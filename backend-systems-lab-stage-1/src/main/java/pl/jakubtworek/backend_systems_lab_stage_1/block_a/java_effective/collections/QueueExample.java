package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import java.util.ArrayDeque;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * ArrayDeque:
 *  - FIFO queue
 *  - fast operations
 *
 * PriorityQueue:
 *  - heap
 *  - ordered by priority
 */
public class QueueExample {

    public static Queue<Integer> createArrayDeque() {
        return new ArrayDeque<>();
    }

    public static Queue<Integer> createPriorityQueue() {
        return new PriorityQueue<>();
    }
}