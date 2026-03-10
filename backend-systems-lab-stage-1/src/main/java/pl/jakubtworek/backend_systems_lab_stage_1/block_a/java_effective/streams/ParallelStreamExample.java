package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.List;

/**
 * This class compares sequential and parallel stream execution
 * for a simple numeric aggregation operation.
 */
public class ParallelStreamExample {

    public long parallelSum(List<Integer> numbers) {

        return numbers.parallelStream()

                // Converts Integer objects to primitive long values.
                // Using a primitive stream (LongStream) avoids boxing overhead
                // during the summation.
                .mapToLong(Integer::longValue)

                // Performs the final reduction operation.
                // In a parallel stream this sum is computed in multiple chunks
                // and then combined into the final result.
                .sum();
    }

    public long sequentialSum(List<Integer> numbers) {

        return numbers.stream()

                // Same conversion to primitive long as in the parallel version.
                // The processing here is strictly single-threaded.
                .mapToLong(Integer::longValue)

                // Sequential reduction: elements are processed in order
                // within a single thread.
                .sum();
    }
}