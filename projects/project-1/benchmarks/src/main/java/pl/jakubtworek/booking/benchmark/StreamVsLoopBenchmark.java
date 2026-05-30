package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class StreamVsLoopBenchmark {
    @State(Scope.Thread)
    public static class Data {
        @Param({"1000", "100000"})
        int size;
        List<Integer> values;

        @Setup(Level.Trial)
        public void setup() {
            values = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                values.add(i);
            }
        }
    }

    @Benchmark
    public void forLoop(Data data, Blackhole blackhole) {
        long sum = 0;
        for (int value : data.values) {
            if ((value & 1) == 0) {
                sum += value * 3L;
            }
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void stream(Data data, Blackhole blackhole) {
        long sum = data.values.stream()
                .filter(value -> (value & 1) == 0)
                .mapToLong(value -> value * 3L)
                .sum();
        blackhole.consume(sum);
    }
}
