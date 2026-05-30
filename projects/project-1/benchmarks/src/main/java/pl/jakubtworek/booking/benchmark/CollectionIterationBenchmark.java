package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class CollectionIterationBenchmark {
    @State(Scope.Thread)
    public static class Data {
        @Param({"1000", "100000"})
        int size;
        List<Integer> arrayList;
        List<Integer> linkedList;

        @Setup(Level.Trial)
        public void setup() {
            arrayList = new ArrayList<>(size);
            linkedList = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                arrayList.add(i);
                linkedList.add(i);
            }
        }
    }

    @Benchmark
    public void arrayListIteration(Data data, Blackhole blackhole) {
        long sum = 0;
        for (Integer value : data.arrayList) {
            sum += value;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void linkedListIteration(Data data, Blackhole blackhole) {
        long sum = 0;
        for (Integer value : data.linkedList) {
            sum += value;
        }
        blackhole.consume(sum);
    }
}
