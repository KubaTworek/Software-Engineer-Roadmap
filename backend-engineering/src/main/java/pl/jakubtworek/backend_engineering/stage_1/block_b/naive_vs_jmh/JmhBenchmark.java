package pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class JmhBenchmark {

    @Param({"1", "42", "1000"})
    private int input;

    private int changingInput;

    @Setup
    public void setup() {
        // Setup is executed outside the measured benchmark operation.
        // This prevents initialization cost from polluting the measured result.
        changingInput = input;
    }

    @Benchmark
    public long returnResult() {
        // Returning the result makes it observable to JMH.
        // JMH prevents the benchmark from being optimized away.
        changingInput++;
        return BenchmarkTarget.compute(changingInput);
    }

    @Benchmark
    public void consumeWithBlackhole(Blackhole blackhole) {
        // Blackhole is used when the benchmark should not return the computed value directly.
        // It prevents dead-code elimination while minimizing artificial side effects.
        changingInput++;
        blackhole.consume(BenchmarkTarget.compute(changingInput));
    }

    @Benchmark
    public long constantInput() {
        // This benchmark intentionally uses a constant-like parameter.
        // It is included to compare with runtime-changing inputs.
        return BenchmarkTarget.compute(input);
    }
}