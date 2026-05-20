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
public class StreamVsLoopBenchmark {

    private static final int SIZE = 1_000_000;

    private int[] primitiveArray;
    private List<Integer> boxedList;

    @org.openjdk.jmh.annotations.Setup
    public void setup() {
        // Primitive array avoids boxing.
        // This is usually the most allocation-friendly representation for hot numeric paths.
        primitiveArray = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            primitiveArray[i] = i;
        }

        // This list intentionally contains boxed Integer values.
        // It is useful for showing the cost of boxing and object-based collections.
        boxedList = IntStream.range(0, SIZE)
                .boxed()
                .toList();
    }

    @Benchmark
    public long forLoopOverPrimitiveArray() {
        // Classic for-loop over primitive array.
        // This version has no iterator allocation, no boxing, and minimal dispatch overhead.
        long sum = 0;

        for (int i = 0; i < primitiveArray.length; i++) {
            int value = primitiveArray[i];

            if ((value & 1) == 0) {
                sum += value * 2L;
            }
        }

        return sum;
    }

    @Benchmark
    public long intStreamOverPrimitiveArray() {
        // IntStream avoids boxing because it works with primitive int values.
        // However, it may still introduce pipeline machinery and additional dispatch overhead.
        return IntStream.of(primitiveArray)
                .filter(value -> (value & 1) == 0)
                .map(value -> value * 2)
                .asLongStream()
                .sum();
    }

    @Benchmark
    public long streamOverBoxedList() {
        // Stream<Integer> operates on boxed Integer objects.
        // Each element is an object reference, and arithmetic requires unboxing.
        //
        // In hot paths, this can increase CPU cost and may contribute to allocation pressure
        // if intermediate operations introduce additional objects.
        return boxedList.stream()
                .filter(value -> (value & 1) == 0)
                .map(value -> value * 2)
                .mapToLong(Integer::longValue)
                .sum();
    }

    @Benchmark
    public long forEachLoopOverBoxedList() {
        // Enhanced for-loop over List<Integer>.
        // This still uses boxed Integer values, but avoids Stream pipeline overhead.
        long sum = 0;

        for (Integer value : boxedList) {
            if ((value & 1) == 0) {
                sum += value * 2L;
            }
        }

        return sum;
    }
}