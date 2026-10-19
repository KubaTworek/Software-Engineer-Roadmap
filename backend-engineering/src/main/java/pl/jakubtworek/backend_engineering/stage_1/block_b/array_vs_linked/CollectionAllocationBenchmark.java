package pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked;

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
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Compares bytes allocated while constructing collections with identical contents.
 * Run with {@code -prof gc} and interpret {@code gc.alloc.rate.norm}.
 * This does not measure retained heap size; use JOL or a heap dump for that question.
 */
@Measures(BenchmarkDimension.ALLOCATION)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class CollectionAllocationBenchmark {

    @Param({"10000", "100000", "1000000"})
    int size;

    @Benchmark
    public ArrayList<Integer> createArrayList() {
        // ArrayList allocates one backing array plus boxed values outside Integer cache.
        ArrayList<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        return list;
    }

    @Benchmark
    public LinkedList<Integer> createLinkedList() {
        // LinkedList additionally allocates one node per element.
        LinkedList<Integer> list = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        return list;
    }
}
