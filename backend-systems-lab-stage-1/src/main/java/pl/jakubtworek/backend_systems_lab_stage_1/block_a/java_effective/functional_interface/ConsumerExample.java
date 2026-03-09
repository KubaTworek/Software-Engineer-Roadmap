package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Consumer;

/**
 * Consumer<T>
 *
 * Accepts a value and performs side effect.
 *
 * No return value.
 */
public class ConsumerExample {

    public static void consume(String value, Consumer<String> consumer) {

        consumer.accept(value);
    }
}