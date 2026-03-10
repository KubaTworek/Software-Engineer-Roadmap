package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.generics;

/**
 * Utility class containing generic helper methods.
 */
public class GenericUtils {

    /**
     * Generic method that swaps two elements in an array.
     *
     * <T> - type of elements stored in the array.
     * The method works with any reference type (e.g. Integer, String, etc.).
     *
     * @param array array in which elements will be swapped
     * @param i index of the first element
     * @param j index of the second element
     */
    public static <T> void swap(T[] array, int i, int j) {

        // temporary variable used to store one element during the swap
        T temp = array[i];

        // move element from position j to position i
        array[i] = array[j];

        // place the stored element into position j
        array[j] = temp;
    }
}