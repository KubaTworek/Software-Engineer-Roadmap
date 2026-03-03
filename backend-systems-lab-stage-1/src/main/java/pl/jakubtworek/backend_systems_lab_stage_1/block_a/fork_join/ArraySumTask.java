package pl.jakubtworek.backend_systems_lab_stage_1.block_a.fork_join;

import java.util.concurrent.RecursiveTask;

/**
 * CPU-bound fork/join task.
 *
 * Good fit for ForkJoinPool because:
 * - no blocking calls
 * - pure computation
 * - recursive divide-and-conquer
 * - work stealing keeps cores busy
 */
public class ArraySumTask extends RecursiveTask<Long> {

    private static final int THRESHOLD = 50_000;

    private final int[] array;
    private final int start;
    private final int end;

    public ArraySumTask(int[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Long compute() {
        int size = end - start;

        if (size <= THRESHOLD) {
            long sum = 0;
            for (int i = start; i < end; i++) {
                sum += array[i];
            }
            return sum;
        }

        int mid = start + size / 2;

        ArraySumTask left = new ArraySumTask(array, start, mid);
        ArraySumTask right = new ArraySumTask(array, mid, end);

        // Standard pattern:
        // fork one side, compute the other, then join
        left.fork();
        long rightResult = right.compute();
        long leftResult = left.join();

        return leftResult + rightResult;
    }
}