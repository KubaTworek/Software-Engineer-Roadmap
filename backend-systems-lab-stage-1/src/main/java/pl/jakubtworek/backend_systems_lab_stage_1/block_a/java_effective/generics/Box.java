package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

/**
 * Simple generic container.
 *
 * T is a type parameter.
 *
 * Advantages:
 * - compile-time type safety
 * - no casts required
 */
public class Box<T> {

    private T value;

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}