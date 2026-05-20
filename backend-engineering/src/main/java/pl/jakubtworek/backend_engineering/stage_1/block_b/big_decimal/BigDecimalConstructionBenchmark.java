package pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal;

import org.openjdk.jmh.annotations.*;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class BigDecimalConstructionBenchmark {

    private static final int SIZE = 10_000;

    private String[] decimalStrings;
    private double[] decimalDoubles;
    private long[] cents;

    @Setup
    public void setup() {
        // Inputs are prepared once.
        // The benchmark methods measure construction strategy cost.
        decimalStrings = new String[SIZE];
        decimalDoubles = new double[SIZE];
        cents = new long[SIZE];

        for (int i = 0; i < SIZE; i++) {
            long value = 1_000 + i;

            cents[i] = value;
            decimalStrings[i] = (value / 100) + "." + (value % 100);
            decimalDoubles[i] = value / 100.0;
        }
    }

    @Benchmark
    public BigDecimal constructFromString() {
        // Constructing BigDecimal from String preserves decimal meaning,
        // but parsing text is relatively expensive.
        BigDecimal total = BigDecimal.ZERO;

        for (String value : decimalStrings) {
            total = total.add(new BigDecimal(value));
        }

        return total;
    }

    @Benchmark
    public BigDecimal constructFromDouble() {
        // This is intentionally shown as a bad practice for money-like values.
        //
        // new BigDecimal(double) uses the exact binary floating-point value,
        // which often produces surprising decimal representations.
        //
        // It can also create expensive BigDecimal values with large internal precision.
        BigDecimal total = BigDecimal.ZERO;

        for (double value : decimalDoubles) {
            total = total.add(new BigDecimal(value));
        }

        return total;
    }

    @Benchmark
    public BigDecimal constructWithValueOfLongScale() {
        // This is usually the preferred construction style
        // when the value is already available as a scaled integer.
        //
        // It avoids parsing and avoids binary floating-point surprises.
        BigDecimal total = BigDecimal.ZERO;

        for (long value : cents) {
            total = total.add(BigDecimal.valueOf(value, 2));
        }

        return total;
    }
}