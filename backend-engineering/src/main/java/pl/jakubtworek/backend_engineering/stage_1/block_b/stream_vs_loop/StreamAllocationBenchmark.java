package pl.jakubtworek.backend_engineering.stage_1.block_b.stream_vs_loop;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking.BenchmarkDimension;
import pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking.Measures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Measures(BenchmarkDimension.ALLOCATION)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class StreamAllocationBenchmark {

    @Param({"1000", "100000"})
    int size;

    private List<Integer> boxedList;

    @org.openjdk.jmh.annotations.Setup
    public void setup() {
        // The source data is prepared once.
        // The benchmark should measure processing cost, not setup cost.
        boxedList = IntStream.range(0, size)
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
    public List<Integer> manualLoopCollectToList() {
        // Both methods materialize the same result. The GC profiler can therefore
        // compare implementation overhead instead of comparing different contracts.
        List<Integer> result = new ArrayList<>((boxedList.size() + 2) / 3);

        for (Integer value : boxedList) {
            if (value % 3 == 0) {
                result.add(value * 2);
            }
        }

        return result;
    }
}
