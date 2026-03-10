package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Function;

/**
 * Example showing how Function can be used to transform
 * one value into another type.
 */
public class FunctionExample {

    /**
     * Applies a transformation to the provided number.
     * The transformation logic is defined outside the method
     * and passed as a Function.
     *
     * @param number value that will be transformed
     * @param function function defining how the number should be converted to String
     * @return result returned by the provided function
     */
    public static String transform(int number, Function<Integer, String> function) {

        // Calls the function and returns the transformed value
        return function.apply(number);
    }

}