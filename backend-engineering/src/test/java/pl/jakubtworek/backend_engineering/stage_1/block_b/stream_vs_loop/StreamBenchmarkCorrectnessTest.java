package pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamBenchmarkCorrectnessTest {

    @Test
    void loopAndStreamVariantsProduceTheSameNumericResult() {
        StreamVsLoopBenchmark benchmark = new StreamVsLoopBenchmark();
        benchmark.size = 257;
        benchmark.setup();

        long expected = benchmark.forLoopOverPrimitiveArray();

        assertThat(benchmark.intStreamOverPrimitiveArray()).isEqualTo(expected);
        assertThat(benchmark.streamOverBoxedList()).isEqualTo(expected);
        assertThat(benchmark.forEachLoopOverBoxedList()).isEqualTo(expected);
    }

    @Test
    void sequentialAndParallelStreamsProduceTheSameResult() {
        ParallelStreamBenchmark benchmark = new ParallelStreamBenchmark();
        benchmark.size = 10_003;
        benchmark.setup();

        assertThat(benchmark.parallelIntStream())
                .isEqualTo(benchmark.sequentialIntStream());
    }

    @Test
    void allocationBenchmarkMaterializesTheSameResultInBothVariants() {
        StreamAllocationBenchmark benchmark = new StreamAllocationBenchmark();
        benchmark.size = 257;
        benchmark.setup();

        List<Integer> streamResult = benchmark.streamCollectToList();
        List<Integer> loopResult = benchmark.manualLoopCollectToList();

        assertThat(loopResult).containsExactlyElementsOf(streamResult);
        assertThat(loopResult).allMatch(value -> value % 6 == 0);
    }
}
