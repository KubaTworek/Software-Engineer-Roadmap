package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.extension_functions.java;

// Java does not support extension functions.
// A common alternative is a utility class with static methods.
public final class StringUtils {

    private StringUtils() {
        // Utility class should not be instantiated.
    }

    public static boolean isEmail(String value) {
        return value.contains("@") && value.contains(".");
    }
}