package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

import java.util.List;

/**
 * Demonstrates wildcard usage.
 */
public class WildcardExample {

    /**
     * Upper bounded wildcard.
     *
     * Read-only producer.
     */
    public static double sum(List<? extends Number> numbers) {

        double result = 0;

        for (Number n : numbers) {
            result += n.doubleValue();
        }

        return result;
    }

    /**
     * Lower bounded wildcard.
     *
     * Consumer of integers.
     */
    public static void addNumbers(List<? super Integer> list) {

        list.add(1);
        list.add(2);
    }
}