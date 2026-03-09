package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

/**
 * Demonstrates generic methods.
 */
public class GenericUtils {

    /**
     * Generic method that swaps array elements.
     */
    public static <T> void swap(T[] array, int i, int j) {

        T temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }
}