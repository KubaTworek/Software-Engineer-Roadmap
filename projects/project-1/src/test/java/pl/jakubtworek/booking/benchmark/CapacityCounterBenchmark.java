package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mikrobenchmark porównuje dwa liczniki o tej samej semantyce: oba zwracają
 * wartość po inkrementacji i oba są bezpieczne współbieżnie. Porównywanie
 * implementacji zwracających inne wyniki byłoby pomiarem różnych programów.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
@Threads(4)
public class CapacityCounterBenchmark {

    @State(Scope.Benchmark)
    public static class Counters {
        private final AtomicInteger atomic = new AtomicInteger();
        private int synchronizedValue;

        int synchronizedIncrement() {
            synchronized (this) {
                return ++synchronizedValue;
            }
        }
    }

    @Benchmark
    public int atomicIncrement(Counters counters) {
        return counters.atomic.incrementAndGet();
    }

    @Benchmark
    public int synchronizedIncrement(Counters counters) {
        return counters.synchronizedIncrement();
    }
}
