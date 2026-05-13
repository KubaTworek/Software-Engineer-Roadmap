package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.top_level_functions.java;

// Java requires methods to belong to a class.
// Utility classes are commonly used for standalone functions.
public final class TaxUtils {

    private TaxUtils() {
        // Utility class should not be instantiated.
    }

    public static double calculateTax(double amount) {
        return amount * 0.23;
    }
}