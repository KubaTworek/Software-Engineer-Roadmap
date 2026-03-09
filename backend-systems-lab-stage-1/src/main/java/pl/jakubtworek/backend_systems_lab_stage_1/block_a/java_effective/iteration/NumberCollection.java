package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Custom collection implementing Iterable.
 *
 * Iterable allows usage in enhanced for loop:
 *
 * for (int n : collection) { }
 *
 * Compiler internally uses Iterator.
 */
public class NumberCollection implements Iterable<Integer> {

    private final int[] data;

    public NumberCollection(int[] data) {
        this.data = data;
    }

    @Override
    public Iterator<Integer> iterator() {
        return new NumberIterator();
    }

    /**
     * Internal iterator implementation.
     */
    private class NumberIterator implements Iterator<Integer> {

        private int index = 0;

        @Override
        public boolean hasNext() {
            return index < data.length;
        }

        @Override
        public Integer next() {

            if (!hasNext())
                throw new NoSuchElementException();

            return data[index++];
        }
    }
}