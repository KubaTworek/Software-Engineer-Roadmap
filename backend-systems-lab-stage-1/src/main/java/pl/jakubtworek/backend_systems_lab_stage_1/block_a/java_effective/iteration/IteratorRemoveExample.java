package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Iterator;
import java.util.List;

/**
 * Example of safe removal of elements during iteration.
 */
public class IteratorRemoveExample {

    public static void removeEven(List<Integer> list) {

        // explicit iterator obtained from the list
        Iterator<Integer> iterator = list.iterator();

        // iterate while there are elements left
        while (iterator.hasNext()) {

            // get next element from iterator
            int value = iterator.next();

            // check if the number is even
            if (value % 2 == 0) {

                // safe removal using iterator
                // keeps iterator state consistent with collection
                iterator.remove();
            }
        }
    }
}