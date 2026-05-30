package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
public class FalseSharingBenchmark {
    @State(Scope.Benchmark)
    public static class ContendedState {
        volatile long left;
        volatile long right;
    }

    @State(Scope.Benchmark)
    public static class PaddedState {
        volatile long left;
        long p1, p2, p3, p4, p5, p6, p7;
        volatile long right;
    }

    @Benchmark
    @Group("falseSharing")
    @GroupThreads(1)
    public void writeLeftContended(ContendedState state) {
        state.left++;
    }

    @Benchmark
    @Group("falseSharing")
    @GroupThreads(1)
    public void writeRightContended(ContendedState state) {
        state.right++;
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(1)
    public void writeLeftPadded(PaddedState state) {
        state.left++;
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(1)
    public void writeRightPadded(PaddedState state) {
        state.right++;
    }
}
