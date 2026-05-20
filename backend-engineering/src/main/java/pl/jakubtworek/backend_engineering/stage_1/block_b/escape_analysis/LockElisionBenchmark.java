package pl.jakubtworek.backend_engineering.stage_1.block_b.escape_analysis;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class LockElisionBenchmark {

    @Benchmark
    public int lockDoesNotEscape() {
        // The lock object is local to this method.
        // No other thread can observe it.
        //
        // Escape Analysis may prove that synchronization is unnecessary.
        // In that case, C2 can eliminate the lock completely.
        Object lock = new Object();

        synchronized (lock) {
            return 42;
        }
    }

    @Benchmark
    public int lockEscapesThroughField(LockState state) {
        // The lock object comes from shared benchmark state.
        // The JIT must assume that it can be observed externally.
        //
        // This makes lock elimination much harder or impossible.
        synchronized (state.lock) {
            return 42;
        }
    }
}