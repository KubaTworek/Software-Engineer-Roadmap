package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Custom Spliterator implementation.
 *
 * Spliterator extends idea of Iterator by enabling
 * parallel processing through splitting.
 *
 * Main methods:
 * - tryAdvance
 * - trySplit
 */
public class NumberSpliterator implements Spliterator<Integer> {

    private final int[] array;
    private int start;
    private final int end;

    public NumberSpliterator(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    /**
     * Processes single element.
     */
    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {

        if (start < end) {

            action.accept(array[start++]);
            return true;
        }

        return false;
    }

    /**
     * Splits work into two parts.
     *
     * Used by parallel streams.
     */
    @Override
    public Spliterator<Integer> trySplit() {

        int size = end - start;

        if (size <= 1)
            return null;

        int mid = start + size / 2;

        Spliterator<Integer> split =
                new NumberSpliterator(array, start, mid);

        start = mid;

        return split;
    }

    @Override
    public long estimateSize() {
        return end - start;
    }

    @Override
    public int characteristics() {

        return ORDERED | SIZED | SUBSIZED | IMMUTABLE;
    }
}