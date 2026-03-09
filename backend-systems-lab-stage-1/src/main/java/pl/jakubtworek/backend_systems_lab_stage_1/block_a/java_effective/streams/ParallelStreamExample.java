package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.streams;

import java.util.List;

/**
 * Parallel streams use ForkJoinPool.commonPool().
 *
 * Good for:
 *  - CPU-bound operations
 *
 * Dangerous for:
 *  - blocking operations
 *  - shared mutable state
 */
public class ParallelStreamExample {

    public long parallelSum(List<Integer> numbers) {

        return numbers.parallelStream()
                .mapToLong(Integer::longValue)
                .sum();
    }

    public long sequentialSum(List<Integer> numbers) {

        return numbers.stream()
                .mapToLong(Integer::longValue)
                .sum();
    }
}