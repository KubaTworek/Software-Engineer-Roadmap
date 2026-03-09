package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Function;

/**
 * Function<T,R>
 *
 * Takes input of type T
 * returns result of type R
 */
public class FunctionExample {

    public static String transform(int number, Function<Integer, String> function) {

        return function.apply(number);
    }

}