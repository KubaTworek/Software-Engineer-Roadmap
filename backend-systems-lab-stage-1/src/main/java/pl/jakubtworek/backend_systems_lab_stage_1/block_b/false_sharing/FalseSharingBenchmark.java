package pl.jakubtworek.backend_systems_lab_stage_1.block_b.false_sharing;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Group)
public class FalseSharingBenchmark {

    private static final int ITERATIONS = 10_000;

    private final UnpaddedCounters unpadded = new UnpaddedCounters();
    private final PaddedCounters padded = new PaddedCounters();

    @Benchmark
    @Group("unpadded")
    @GroupThreads(1)
    public void unpaddedCounter1() {
        // Thread 1 updates counter1.
        // counter1 and counter2 are logically independent,
        // but they may live on the same CPU cache line.
        for (int i = 0; i < ITERATIONS; i++) {
            unpadded.counter1++;
        }
    }

    @Benchmark
    @Group("unpadded")
    @GroupThreads(1)
    public void unpaddedCounter2() {
        // Thread 2 updates counter2.
        // If counter1 and counter2 share one cache line,
        // CPU cores will repeatedly invalidate each other's cache line.
        for (int i = 0; i < ITERATIONS; i++) {
            unpadded.counter2++;
        }
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(1)
    public void paddedCounter1() {
        // Thread 1 updates counter1.
        // Padding separates hot fields into different cache lines.
        for (int i = 0; i < ITERATIONS; i++) {
            padded.counter1.value++;
        }
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(1)
    public void paddedCounter2() {
        // Thread 2 updates counter2.
        // Since each counter is isolated, cache line bouncing should be reduced.
        for (int i = 0; i < ITERATIONS; i++) {
            padded.counter2.value++;
        }
    }
}