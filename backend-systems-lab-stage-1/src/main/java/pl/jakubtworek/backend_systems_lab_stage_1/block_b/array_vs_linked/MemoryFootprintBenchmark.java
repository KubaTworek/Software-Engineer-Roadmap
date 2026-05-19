package pl.jakubtworek.backend_systems_lab_stage_1.block_b.array_vs_linked;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class MemoryFootprintBenchmark {

    @Param({"10000", "100000", "1000000"})
    private int size;

    @Benchmark
    public ArrayList<Integer> createArrayList() {
        // ArrayList allocates one backing array plus boxed Integer references.
        // The collection structure itself is compact.
        ArrayList<Integer> list = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        return list;
    }

    @Benchmark
    public LinkedList<Integer> createLinkedList() {
        // LinkedList allocates one Node object per element.
        //
        // Each node contains:
        // - reference to the item,
        // - reference to the previous node,
        // - reference to the next node,
        // - object header.
        //
        // This increases allocation pressure and memory footprint.
        LinkedList<Integer> list = new LinkedList<>();

        for (int i = 0; i < size; i++) {
            list.add(i);
        }

        return list;
    }
}