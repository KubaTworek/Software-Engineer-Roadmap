package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(8)
public class CounterBenchmark {
    @State(Scope.Benchmark)
    public static class Counters {
        long synchronizedCounter;
        AtomicLong atomicLong = new AtomicLong();
        LongAdder longAdder = new LongAdder();

        synchronized void incrementSynchronized() {
            synchronizedCounter++;
        }
    }

    @Benchmark
    public void synchronizedCounter(Counters counters) {
        counters.incrementSynchronized();
    }

    @Benchmark
    public void atomicLong(Counters counters) {
        counters.atomicLong.incrementAndGet();
    }

    @Benchmark
    public void longAdder(Counters counters) {
        counters.longAdder.increment();
    }
}
