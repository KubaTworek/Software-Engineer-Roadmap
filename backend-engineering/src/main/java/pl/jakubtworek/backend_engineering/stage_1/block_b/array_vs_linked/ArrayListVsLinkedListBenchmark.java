package pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ArrayListVsLinkedListBenchmark {

    @Param({"1000", "10000", "100000"})
    private int size;

    private ArrayList<Integer> arrayList;
    private LinkedList<Integer> linkedList;

    @Setup
    public void setup() {
        // ArrayList stores references in one contiguous backing array.
        // This usually gives much better cache locality during iteration.
        arrayList = new ArrayList<>(size);

        // LinkedList stores each element in a separate node object.
        // Nodes are scattered across the heap, which usually hurts cache locality.
        linkedList = new LinkedList<>();

        for (int i = 0; i < size; i++) {
            Integer value = i;
            arrayList.add(value);
            linkedList.add(value);
        }
    }

    @Benchmark
    public long iterateArrayList() {
        // Iteration over ArrayList is usually cache-friendly.
        // The backing array is contiguous, so the CPU can prefetch memory efficiently.
        long sum = 0;

        for (Integer value : arrayList) {
            sum += value;
        }

        return sum;
    }

    @Benchmark
    public long iterateLinkedList() {
        // Iteration over LinkedList follows node references one by one.
        // This pointer chasing often causes cache misses and poor CPU utilization.
        long sum = 0;

        for (Integer value : linkedList) {
            sum += value;
        }

        return sum;
    }

    @Benchmark
    public long indexAccessArrayList() {
        // Random-like index access on ArrayList is O(1).
        // The element reference can be loaded directly from the backing array.
        long sum = 0;

        for (int i = 0; i < arrayList.size(); i += 16) {
            sum += arrayList.get(i);
        }

        return sum;
    }

    @Benchmark
    public long indexAccessLinkedList() {
        // Index access on LinkedList is O(n).
        // Each get(i) must walk through nodes from the beginning or end.
        //
        // This benchmark intentionally demonstrates why LinkedList is a poor choice
        // for indexed access.
        long sum = 0;

        for (int i = 0; i < linkedList.size(); i += 16) {
            sum += linkedList.get(i);
        }

        return sum;
    }
}