package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Spliterator;
import java.util.stream.StreamSupport;

/**
 * Example showing how a Spliterator can be converted into a Stream.
 */
public class SpliteratorExample {

    public static int sum(int[] numbers) {

        // create custom Spliterator for the whole array range
        Spliterator<Integer> spliterator =
                new NumberSpliterator(numbers, 0, numbers.length);

        return StreamSupport
                // create sequential stream from Spliterator
                .stream(spliterator, false)

                // convert Integer objects to primitive int
                .mapToInt(Integer::intValue)

                // aggregate all elements by summing them
                .sum();
    }
}