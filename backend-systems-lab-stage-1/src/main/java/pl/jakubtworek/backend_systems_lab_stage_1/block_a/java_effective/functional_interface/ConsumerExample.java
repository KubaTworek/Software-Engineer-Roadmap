package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Consumer;

/**
 * Example showing how Consumer can be passed to a method
 * to perform an operation on a given value without returning a result.
 */
public class ConsumerExample {

    /**
     * Passes the provided value to the Consumer.
     * The method itself does not define what happens with the value.
     * The behavior is defined externally (e.g. lambda expression).
     *
     * @param value value that will be processed
     * @param consumer action that should be executed on the value
     */
    public static void consume(String value, Consumer<String> consumer) {

        // Executes the action defined in the Consumer implementation
        consumer.accept(value);
    }
}