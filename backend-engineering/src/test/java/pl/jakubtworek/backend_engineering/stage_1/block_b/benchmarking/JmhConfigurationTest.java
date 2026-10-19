package pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked.ArrayListVsLinkedListBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked.InsertionBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked.CollectionAllocationBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked.PrimitiveArrayBaselineBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal.BigDecimalConstructionBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal.BigDecimalHotLoopBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal.MoneyRepresentationBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.escape_analysis.EscapeAnalysisBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.escape_analysis.LockElisionBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.false_sharing.FalseSharingBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh.JmhBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop.ParallelStreamBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop.StreamAllocationBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop.StreamVsLoopBenchmark;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JmhConfigurationTest {

    private static final List<Class<?>> BENCHMARKS = List.of(
            JmhBenchmark.class,
            StreamVsLoopBenchmark.class,
            ParallelStreamBenchmark.class,
            StreamAllocationBenchmark.class,
            ArrayListVsLinkedListBenchmark.class,
            PrimitiveArrayBaselineBenchmark.class,
            InsertionBenchmark.class,
            CollectionAllocationBenchmark.class,
            BigDecimalConstructionBenchmark.class,
            BigDecimalHotLoopBenchmark.class,
            MoneyRepresentationBenchmark.class,
            EscapeAnalysisBenchmark.class,
            LockElisionBenchmark.class,
            FalseSharingBenchmark.class
    );

    @Test
    void everyBenchmarkDeclaresWarmupMeasurementForkModeAndUnit() {
        for (Class<?> benchmark : BENCHMARKS) {
            assertThat(benchmark.getAnnotation(Warmup.class))
                    .as("warmup on %s", benchmark.getSimpleName())
                    .satisfies(warmup -> {
                        assertThat(warmup.iterations()).isEqualTo(5);
                        assertThat(warmup.time()).isEqualTo(1);
                    });
            assertThat(benchmark.getAnnotation(Measurement.class))
                    .as("measurement on %s", benchmark.getSimpleName())
                    .satisfies(measurement -> {
                        assertThat(measurement.iterations()).isEqualTo(5);
                        assertThat(measurement.time()).isEqualTo(1);
                    });
            assertThat(benchmark.getAnnotation(Fork.class))
                    .as("fork on %s", benchmark.getSimpleName())
                    .satisfies(fork -> assertThat(fork.value()).isGreaterThanOrEqualTo(2));
            assertThat(benchmark.getAnnotation(BenchmarkMode.class)).isNotNull();
            assertThat(benchmark.getAnnotation(OutputTimeUnit.class)).isNotNull();
            assertThat(benchmark.getDeclaredMethods())
                    .anyMatch(method -> method.isAnnotationPresent(Benchmark.class));
        }
    }

    @Test
    void everyBenchmarkHasOneExplicitPrimaryDimension() {
        assertThat(BENCHMARKS)
                .allSatisfy(type -> assertThat(type.getAnnotation(Measures.class)).isNotNull());

        assertThat(BENCHMARKS).filteredOn(type ->
                type.getAnnotation(Measures.class).value() == BenchmarkDimension.THROUGHPUT
        ).hasSize(7);
        assertThat(BENCHMARKS).filteredOn(type ->
                type.getAnnotation(Measures.class).value() == BenchmarkDimension.LATENCY
        ).containsExactly(InsertionBenchmark.class);
        assertThat(BENCHMARKS).filteredOn(type ->
                type.getAnnotation(Measures.class).value() == BenchmarkDimension.ALLOCATION
        ).hasSize(6);
    }
}
