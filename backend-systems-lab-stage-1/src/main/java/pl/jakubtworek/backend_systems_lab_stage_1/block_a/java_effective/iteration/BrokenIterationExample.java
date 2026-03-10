package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.List;

/**
 * Example of incorrect removal during foreach iteration.
 */
public class BrokenIterationExample {

    public static void brokenRemove(List<Integer> list) {

        // foreach internally uses an Iterator
        for (Integer value : list) {

            // condition selecting elements to remove
            if (value % 2 == 0) {

                // removing directly from the list modifies its structure
                // while the iterator used by foreach is still active
                // this causes ConcurrentModificationException
                list.remove(value);
            }
        }
    }
}