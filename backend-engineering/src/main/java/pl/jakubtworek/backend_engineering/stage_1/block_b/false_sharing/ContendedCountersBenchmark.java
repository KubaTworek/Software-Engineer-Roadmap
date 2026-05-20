package pl.jakubtworek.backend_engineering.stage_1.block_b.false_sharing;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
        value = 2,
        jvmArgsAppend = {
                // Required for @Contended outside JDK internal classes.
                "-XX:-RestrictContended"
        }
)
@State(Scope.Group)
public class ContendedCountersBenchmark {

    private static final int ITERATIONS = 10_000;

    private final ContendedCounters counters = new ContendedCounters();

    @Benchmark
    @Group("contended")
    @GroupThreads(1)
    public void contendedCounter1() {
        // @Contended asks the JVM to isolate this field from nearby fields.
        for (int i = 0; i < ITERATIONS; i++) {
            counters.counter1++;
        }
    }

    @Benchmark
    @Group("contended")
    @GroupThreads(1)
    public void contendedCounter2() {
        // This should reduce false sharing compared to two adjacent volatile fields.
        for (int i = 0; i < ITERATIONS; i++) {
            counters.counter2++;
        }
    }
}