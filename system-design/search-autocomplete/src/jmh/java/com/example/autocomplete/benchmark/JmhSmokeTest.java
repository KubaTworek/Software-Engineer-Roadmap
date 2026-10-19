package com.example.autocomplete.benchmark;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class JmhSmokeTest {
    @Test
    void benchmarkConfigurationIsExecutable() throws Exception {
        Collection<RunResult> results = new Runner(new OptionsBuilder()
                .include(TrieLookupBenchmark.class.getSimpleName())
                .warmupIterations(1).measurementIterations(1).forks(1)
                .shouldFailOnError(true).build()).run();
        assertThat(results).isNotEmpty();
    }
}
