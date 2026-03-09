package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Spliterator;
import java.util.stream.StreamSupport;

/**
 * Converts Spliterator to Stream.
 */
public class SpliteratorExample {

    public static int sum(int[] numbers) {

        Spliterator<Integer> spliterator =
                new NumberSpliterator(numbers,0,numbers.length);

        return StreamSupport
                .stream(spliterator,false)
                .mapToInt(Integer::intValue)
                .sum();
    }
}