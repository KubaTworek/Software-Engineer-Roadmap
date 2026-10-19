package pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked.InsertionBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh.JmhBenchmark;
import pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop.StreamAllocationBenchmark;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "jmh.smoke", matches = "true")
class JmhSmokeTest {

    @Test
    void executesOneShortBenchmarkForEveryMeasurementDimension() throws Exception {
        assertSuccessfulRun(new Runner(smokeOptions(JmhBenchmark.class, "returnResult")
                .param("input", "42")
                .build()).run());

        assertSuccessfulRun(new Runner(smokeOptions(InsertionBenchmark.class, "insertMiddleArrayList")
                .param("size", "1000")
                .build()).run());

        Collection<RunResult> allocationResults = new Runner(
                smokeOptions(StreamAllocationBenchmark.class, "streamCollectToList")
                .param("size", "1000")
                .addProfiler(GCProfiler.class)
                .build()
        ).run();

        assertSuccessfulRun(allocationResults);
        assertThat(allocationResults).singleElement().satisfies(result ->
                assertThat(result.getSecondaryResults()).containsKey("gc.alloc.rate.norm")
        );
    }

    private static ChainedOptionsBuilder smokeOptions(Class<?> benchmark, String method) {
        return new OptionsBuilder()
                .include("^" + benchmark.getName() + "\\." + method + "$")
                .warmupIterations(1)
                .warmupTime(TimeValue.milliseconds(100))
                .measurementIterations(1)
                .measurementTime(TimeValue.milliseconds(100))
                .forks(1)
                .shouldFailOnError(true);
    }

    private static void assertSuccessfulRun(Collection<RunResult> results) {
        assertThat(results).singleElement().satisfies(result ->
                assertThat(result.getPrimaryResult().getScore()).isPositive().isFinite()
        );
    }
}
