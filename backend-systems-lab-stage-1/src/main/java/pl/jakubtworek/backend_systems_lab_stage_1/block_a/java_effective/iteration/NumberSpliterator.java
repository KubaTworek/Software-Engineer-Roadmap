package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Example of custom Spliterator for array traversal.
 */
public class NumberSpliterator implements Spliterator<Integer> {

    // source data
    private final int[] array;

    // current position of iteration
    private int start;

    // end boundary (exclusive)
    private final int end;

    public NumberSpliterator(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    /**
     * Processes a single element if available.
     */
    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {

        // check if elements remain
        if (start < end) {

            // pass current element to consumer and move forward
            action.accept(array[start++]);
            return true;
        }

        // no elements left
        return false;
    }

    /**
     * Splits the remaining range into two parts.
     */
    @Override
    public Spliterator<Integer> trySplit() {

        // number of remaining elements
        int size = end - start;

        // too small to split further
        if (size <= 1)
            return null;

        // midpoint of current range
        int mid = start + size / 2;

        // new spliterator handles first half
        Spliterator<Integer> split =
                new NumberSpliterator(array, start, mid);

        // current spliterator continues from midpoint
        start = mid;

        return split;
    }

    @Override
    public long estimateSize() {
        // estimated remaining elements
        return end - start;
    }

    @Override
    public int characteristics() {

        // properties of this spliterator
        return ORDERED | SIZED | SUBSIZED | IMMUTABLE;
    }
}