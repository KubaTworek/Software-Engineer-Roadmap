package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import org.junit.jupiter.api.Test;

import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

class QueueExampleTest {

    @Test
    void arrayDequeShouldBeFifo() {

        Queue<Integer> q = QueueExample.createArrayDeque();

        q.add(1);
        q.add(2);

        assertEquals(1, q.poll());
    }

    @Test
    void priorityQueueShouldReturnSmallest() {

        Queue<Integer> q = QueueExample.createPriorityQueue();

        q.add(5);
        q.add(1);
        q.add(3);

        assertEquals(1, q.poll());
    }
}