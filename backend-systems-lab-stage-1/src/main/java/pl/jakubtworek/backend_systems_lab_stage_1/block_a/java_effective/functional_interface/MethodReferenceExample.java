package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Function;

/**
 * Example demonstrating how a method reference can be used
 * as an implementation of the Function interface.
 */
public class MethodReferenceExample {

    /**
     * Converts an integer value to String.
     * This method can be passed as a method reference (e.g. MethodReferenceExample::convert)
     * where a Function<Integer, String> is expected.
     */
    public static String convert(int value) {

        // Simple conversion of int to String
        return String.valueOf(value);
    }

    /**
     * Executes the provided function on the given value.
     * The method itself does not define the conversion logic,
     * it delegates the operation to the passed Function.
     *
     * @param function function that processes the value
     * @param value input passed to the function
     * @return result produced by the function
     */
    public static String apply(Function<Integer, String> function, int value) {

        // Calls the provided function implementation
        return function.apply(value);
    }
}