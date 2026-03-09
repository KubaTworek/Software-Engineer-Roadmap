package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Function;

/**
 * Method reference syntax.
 */
public class MethodReferenceExample {

    public static String convert(int value) {

        return String.valueOf(value);
    }

    public static String apply(Function<Integer, String> function, int value) {

        return function.apply(value);
    }
}