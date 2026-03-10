package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Predicate;

/**
 * Example showing how Predicate can be passed to a method
 * to evaluate a condition on a given value.
 */
public class PredicateExample {

    /**
     * Evaluates the given value using the provided predicate.
     * The condition logic is defined outside this method
     * (for example as a lambda expression).
     *
     * @param value value to be tested
     * @param predicate condition that will be applied to the value
     * @return result of predicate evaluation (true or false)
     */
    public static boolean test(int value, Predicate<Integer> predicate) {

        // Executes the predicate logic on the provided value
        return predicate.test(value);
    }
}