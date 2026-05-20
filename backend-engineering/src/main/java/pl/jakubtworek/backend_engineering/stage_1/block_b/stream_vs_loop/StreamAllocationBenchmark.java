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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class StreamAllocationBenchmark {

    private static final int SIZE = 100_000;

    private List<Integer> boxedList;

    @org.openjdk.jmh.annotations.Setup
    public void setup() {
        // The source data is prepared once.
        // The benchmark should measure processing cost, not setup cost.
        boxedList = IntStream.range(0, SIZE)
                .boxed()
                .toList();
    }

    @Benchmark
    public List<Integer> streamCollectToList() {
        // This benchmark intentionally allocates a new result list.
        // Stream pipelines that collect results can create allocation pressure.
        return boxedList.stream()
                .filter(value -> value % 3 == 0)
                .map(value -> value * 2)
                .toList();
    }

    @Benchmark
    public int manualLoopNoResultAllocation() {
        // This version does equivalent numeric work without creating a result collection.
        // It is not semantically identical to collectToList(), but shows how avoiding materialization
        // can reduce allocation pressure in hot paths.
        int sum = 0;

        for (Integer value : boxedList) {
            if (value % 3 == 0) {
                sum += value * 2;
            }
        }

        return sum;
    }
}