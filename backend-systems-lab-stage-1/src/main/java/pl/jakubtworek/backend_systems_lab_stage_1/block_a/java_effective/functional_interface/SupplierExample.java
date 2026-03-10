package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Supplier;

/**
 * Example showing how Supplier can be used to provide a value
 * without passing any input parameters.
 */
public class SupplierExample {

    /**
     * Retrieves a value from the provided Supplier.
     * The method itself does not define how the value is created.
     * The logic for generating the value is provided externally.
     *
     * @param supplier object responsible for supplying the value
     * @return value returned by the supplier
     */
    public static String get(Supplier<String> supplier) {

        // Calls the supplier to obtain the value
        return supplier.get();
    }
}