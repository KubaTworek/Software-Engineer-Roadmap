package pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class PrimitiveArrayBaselineBenchmark {

    @Param({"1000", "10000", "100000"})
    private int size;

    private int[] primitiveArray;
    private ArrayList<Integer> arrayList;

    @Setup
    public void setup() {
        // Primitive array is the locality baseline.
        // Values are stored directly and contiguously in memory.
        primitiveArray = new int[size];

        arrayList = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            primitiveArray[i] = i;
            arrayList.add(i);
        }
    }

    @Benchmark
    public long iteratePrimitiveArray() {
        // This is usually the most CPU-cache-friendly representation.
        long sum = 0;

        for (int value : primitiveArray) {
            sum += value;
        }

        return sum;
    }

    @Benchmark
    public long iterateArrayListOfInteger() {
        // ArrayList is compact as a collection structure,
        // but Integer values are still boxed objects.
        //
        // The backing array stores references, not raw int values.
        long sum = 0;

        for (Integer value : arrayList) {
            sum += value;
        }

        return sum;
    }
}