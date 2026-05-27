package pl.jakubtworek.backend_engineering.stage_1.block_a.fork_join;

import java.util.concurrent.RecursiveTask;

/**
 * Task that calculates the sum of a portion of an array.
 * Each task works on a specific index range [start, end).
 * RecursiveTask<Long> is used because the computation returns a result.
 */
public class ArraySumTask extends RecursiveTask<Long> {

    // When the sub-array size becomes small enough,
    // the computation switches to a sequential loop
    // to avoid overhead of creating more tasks.
    private static final int THRESHOLD = 50_000;

    // Shared input array used by all subtasks
    private final int[] array;

    // Start index of the processed range (inclusive)
    private final int start;

    // End index of the processed range (exclusive)
    private final int end;

    public ArraySumTask(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Long compute() {
        int size = end - start; // number of elements in this task's range

        // If the range is small enough, process it sequentially
        if (size <= THRESHOLD) {
            long sum = 0;

            // Sum only the assigned portion of the array
            for (int i = start; i < end; i++) {
                sum += array[i];
            }

            return sum;
        }

        // Split the task into two smaller subtasks
        int mid = start + size / 2;

        ArraySumTask left = new ArraySumTask(array, start, mid);
        ArraySumTask right = new ArraySumTask(array, mid, end);

        // Submit the left task asynchronously to the ForkJoinPool
        left.fork();

        // Compute the right task directly in the current thread
        long rightResult = right.compute();

        // Wait for the left task to finish and retrieve its result
        long leftResult = left.join();

        // Combine results from both subtasks
        return leftResult + rightResult;
    }
}