package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

/**
 * Generic container that stores a single value of type T.
 * The actual type of T is specified when the object is created.
 *
 * Example usage:
 * Box<Integer> intBox = new Box<>();
 * intBox.set(10);
 * Integer value = intBox.get();
 */
public class Box<T> {

    // Field that stores the value of type T
    private T value;

    // Stores a value inside the box
    // The argument must match the type used when creating the Box
    public void set(T value) {
        this.value = value;
    }

    // Returns the stored value
    // The returned type is the same type that the Box was created with
    public T get() {
        return value;
    }
}