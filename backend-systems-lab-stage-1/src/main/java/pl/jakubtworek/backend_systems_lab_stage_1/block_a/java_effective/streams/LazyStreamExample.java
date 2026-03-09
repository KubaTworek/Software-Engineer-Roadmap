package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.List;

/**
 * Streams are lazy.
 *
 * Intermediate operations (map/filter)
 * are NOT executed until terminal operation appears.
 */
public class LazyStreamExample {

    public static void demonstrateLazyEvaluation(List<Integer> numbers) {

        numbers.stream()
                .map(n -> {
                    System.out.println("Mapping: " + n);
                    return n * 2;
                })
                .filter(n -> {
                    System.out.println("Filtering: " + n);
                    return n > 5;
                })
                .findFirst(); // terminal operation
    }
}