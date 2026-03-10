package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

/**
 * Generic container that accepts only numeric types.
 *
 * The type parameter T is restricted to subclasses of Number
 * (e.g. Integer, Double, Float, Long).
 */
public class NumberBox<T extends Number> {

    // Stores a numeric value of type T
    private T value;

    // Constructor initializing the box with a numeric value
    public NumberBox(T value) {
        this.value = value;
    }

    public double doubleValue() {

        // Because T extends Number, we can safely call methods
        // defined in the Number class (e.g. doubleValue())
        return value.doubleValue();
    }
}