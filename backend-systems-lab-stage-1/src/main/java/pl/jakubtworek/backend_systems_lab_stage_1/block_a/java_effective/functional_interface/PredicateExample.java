package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Predicate;

/**
 * Predicate<T>
 *
 * Returns boolean.
 *
 * Commonly used in:
 * - filtering
 * - validation
 */
public class PredicateExample {

    public static boolean test(int value, Predicate<Integer> predicate) {

        return predicate.test(value);
    }
}