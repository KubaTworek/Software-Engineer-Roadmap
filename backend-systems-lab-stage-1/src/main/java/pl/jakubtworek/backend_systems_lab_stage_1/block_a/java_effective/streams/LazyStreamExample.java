package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.List;

/**
 * This example demonstrates that stream operations are not executed
 * until a terminal operation is invoked (here: findFirst()).
 *
 * It also shows that operations are processed element-by-element
 * through the entire pipeline rather than stage-by-stage.
 */
public class LazyStreamExample {

    public static void demonstrateLazyEvaluation(List<Integer> numbers) {

        numbers.stream()

                // Each element from the list enters the stream pipeline here.
                // We print the value before transformation to observe
                // when the mapping actually happens.
                .map(n -> {
                    System.out.println("Mapping: " + n);
                    return n * 2; // transform the element
                })

                // The mapped value is immediately passed to the filter.
                // At this point the value is already multiplied by 2.
                // Only numbers greater than 5 will pass the filter.
                .filter(n -> {
                    System.out.println("Filtering: " + n);
                    return n > 5;
                })

                // Terminal operation that triggers execution of the stream pipeline.
                // The stream stops processing as soon as the first matching element
                // is found due to the short-circuiting behavior of findFirst().
                .findFirst();
    }
}