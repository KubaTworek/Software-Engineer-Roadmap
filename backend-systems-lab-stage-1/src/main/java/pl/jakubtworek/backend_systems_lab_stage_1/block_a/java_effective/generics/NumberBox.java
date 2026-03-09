package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

/**
 * Bounded type parameter.
 *
 * T must extend Number.
 */
public class NumberBox<T extends Number> {

    private T value;

    public NumberBox(T value) {
        this.value = value;
    }

    public double doubleValue() {
        return value.doubleValue();
    }
}