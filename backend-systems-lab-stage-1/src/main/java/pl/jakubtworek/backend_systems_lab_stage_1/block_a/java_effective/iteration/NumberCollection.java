package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.iteration;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Example of a custom collection that can be used in enhanced for-loop.
 */
public class NumberCollection implements Iterable<Integer> {

    // internal storage for elements
    private final int[] data;

    public NumberCollection(int[] data) {
        this.data = data;
    }

    @Override
    public Iterator<Integer> iterator() {
        // returns iterator instance used by enhanced for-loop
        return new NumberIterator();
    }

    /**
     * Iterator responsible for traversing the collection.
     */
    private class NumberIterator implements Iterator<Integer> {

        // current position in the array
        private int index = 0;

        @Override
        public boolean hasNext() {
            // checks if there are more elements
            return index < data.length;
        }

        @Override
        public Integer next() {

            // required check before accessing next element
            if (!hasNext())
                throw new NoSuchElementException();

            // return current element and move iterator forward
            return data[index++];
        }
    }
}