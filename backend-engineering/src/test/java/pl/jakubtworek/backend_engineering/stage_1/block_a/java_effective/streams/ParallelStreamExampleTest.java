package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.streams;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ParallelStreamExampleTest {

    @Test
    void parallelAndSequentialShouldProduceSameResult() {

        List<Integer> numbers =
                IntStream.rangeClosed(1, 1_000_000)
                        .boxed()
                        .toList();

        ParallelStreamExample example =
                new ParallelStreamExample();

        long sequential = example.sequentialSum(numbers);
        long parallel = example.parallelSum(numbers);

        assertEquals(sequential, parallel);
    }
}