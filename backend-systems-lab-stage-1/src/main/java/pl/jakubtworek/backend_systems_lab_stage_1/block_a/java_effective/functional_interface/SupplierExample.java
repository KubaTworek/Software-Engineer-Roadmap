package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.functional_interface;

import java.util.function.Supplier;

/**
 * Supplier<T>
 *
 * Produces a value without input.
 */
public class SupplierExample {

    public static String get(Supplier<String> supplier) {

        return supplier.get();
    }
}