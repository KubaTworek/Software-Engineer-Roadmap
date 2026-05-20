package pl.jakubtworek.backend_engineering.stage_1.block_b.escape_analysis;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(
        value = 2,
        jvmArgsAppend = {
                // Print JIT compilation information.
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintCompilation"
        }
)
@State(Scope.Thread)
public class EscapeAnalysisBenchmark {

    private int base = 10;

    @Benchmark
    public int objectDoesNotEscape() {
        // The Point object is created and used only inside this method.
        // It does not escape to a field, array, static variable, or another thread.
        //
        // C2 may apply Escape Analysis and Scalar Replacement here.
        // The allocation can disappear completely in optimized machine code.
        Point point = new Point(base, base + 1);

        // Only the scalar values are needed.
        // The object identity is irrelevant.
        return point.sum();
    }

    @Benchmark
    public int objectEscapesThroughField(EscapeState state) {
        // The object is stored into a field.
        // This makes it observable outside the current method.
        //
        // Escape Analysis usually cannot eliminate this allocation,
        // because the object now escapes the local scope.
        state.point = new Point(base, base + 1);

        return state.point.sum();
    }

    @Benchmark
    public int objectEscapesThroughReturn() {
        // The created object is returned from another method.
        // From the caller's perspective, the object may escape.
        //
        // Depending on inlining and optimization context,
        // the JIT may or may not eliminate this allocation.
        Point point = createPoint(base);

        return point.sum();
    }

    private Point createPoint(int value) {
        // This object is returned to the caller.
        // Return itself does not always prevent optimization,
        // but it makes the analysis more dependent on inlining.
        return new Point(value, value + 1);
    }
}