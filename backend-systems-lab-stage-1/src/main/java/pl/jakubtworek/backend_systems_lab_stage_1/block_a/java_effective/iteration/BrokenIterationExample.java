package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.List;

/**
 * Incorrect modification during iteration.
 *
 * This will trigger ConcurrentModificationException
 * because collection structure changes outside iterator.
 */
public class BrokenIterationExample {

    public static void brokenRemove(List<Integer> list) {

        for (Integer value : list) {

            if (value % 2 == 0) {
                list.remove(value);
            }
        }
    }
}