package pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ParallelStreamBenchmark {

    private static final int SIZE = 1_000_000;

    private int[] primitiveArray;

    @org.openjdk.jmh.annotations.Setup
    public void setup() {
        // A primitive array is used to isolate parallel stream overhead
        // from boxing and collection overhead.
        primitiveArray = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            primitiveArray[i] = i;
        }
    }

    @Benchmark
    public long sequentialIntStream() {
        // Sequential stream keeps the work on the current thread.
        // It has pipeline overhead, but no fork-join coordination cost.
        return IntStream.of(primitiveArray)
                .filter(value -> (value & 1) == 0)
                .map(value -> value * 2)
                .asLongStream()
                .sum();
    }

    @Benchmark
    public long parallelIntStream() {
        // Parallel stream splits work across ForkJoinPool.commonPool().
        // This can help for large CPU-heavy workloads.
        //
        // For small or memory-bound workloads, coordination overhead can dominate.
        return IntStream.of(primitiveArray)
                .parallel()
                .filter(value -> (value & 1) == 0)
                .map(value -> value * 2)
                .asLongStream()
                .sum();
    }
}