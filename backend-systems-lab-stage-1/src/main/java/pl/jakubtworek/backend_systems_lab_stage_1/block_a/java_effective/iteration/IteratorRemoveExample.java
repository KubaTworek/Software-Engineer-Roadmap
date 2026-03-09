package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Iterator;
import java.util.List;

/**
 * Demonstrates correct removal during iteration.
 *
 * Removing elements directly from list during iteration
 * causes ConcurrentModificationException.
 *
 * Correct approach:
 * iterator.remove()
 */
public class IteratorRemoveExample {

    public static void removeEven(List<Integer> list) {

        Iterator<Integer> iterator = list.iterator();

        while (iterator.hasNext()) {

            int value = iterator.next();

            if (value % 2 == 0) {
                iterator.remove();
            }
        }
    }
}