package pl.jakubtworek.booking.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JmhSmokeTest {

    @Test
    @EnabledIfSystemProperty(named = "jmh.smoke", matches = "true")
    void benchmarkHarnessStartsAndProducesFiniteScores() throws Exception {
        Options options = new OptionsBuilder()
                .include(CapacityCounterBenchmark.class.getSimpleName())
                // Smoke ogranicza czas CI. Nie wolno interpretować jego wyniku
                // jako stabilnego porównania wydajnościowego.
                .warmupIterations(1)
                .measurementIterations(1)
                .warmupTime(org.openjdk.jmh.runner.options.TimeValue.milliseconds(100))
                .measurementTime(org.openjdk.jmh.runner.options.TimeValue.milliseconds(100))
                .forks(1)
                .timeUnit(TimeUnit.SECONDS)
                .shouldFailOnError(true)
                .build();

        Collection<RunResult> results = new Runner(options).run();

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(result ->
                assertThat(result.getPrimaryResult().getScore()).isFinite().isPositive());
    }
}
