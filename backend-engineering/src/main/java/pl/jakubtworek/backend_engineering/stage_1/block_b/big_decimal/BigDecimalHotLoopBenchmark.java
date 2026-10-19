package pl.jakubtworek.backend_engineering.stage_1.block_b.big_decimal;

import org.openjdk.jmh.annotations.*;
import pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking.BenchmarkDimension;
import pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking.Measures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

@Measures(BenchmarkDimension.ALLOCATION)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class BigDecimalHotLoopBenchmark {

    private static final int SIZE = 10_000;

    private BigDecimal[] prices;
    private BigDecimal[] rates;
    private long[] pricesInCents;
    private int[] ratesBasisPoints;

    @Setup
    public void setup() {
        // Test data is created once outside the benchmarked methods.
        // The benchmark should measure arithmetic cost, not setup cost.
        prices = new BigDecimal[SIZE];
        rates = new BigDecimal[SIZE];

        pricesInCents = new long[SIZE];
        ratesBasisPoints = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            long cents = 1_000 + i;

            // BigDecimal.valueOf(long, scale) is preferred over new BigDecimal(double),
            // because it avoids binary floating-point surprises.
            prices[i] = BigDecimal.valueOf(cents, 2);

            // Example rate: 0.01, 0.02, ..., 0.25
            int basisPoints = 100 + (i % 25) * 100;
            rates[i] = BigDecimal.valueOf(basisPoints, 4);

            pricesInCents[i] = cents;
            ratesBasisPoints[i] = basisPoints;
        }
    }

    @Benchmark
    public BigDecimal bigDecimalHotLoopWithRounding() {
        // Rounding inside the loop makes the cost even more visible.
        //
        // setScale() may allocate a new BigDecimal and perform additional arithmetic.
        // Doing this per iteration is often much more expensive than rounding once at the boundary.
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < SIZE; i++) {
            BigDecimal tax = prices[i]
                    .multiply(rates[i])
                    .setScale(2, RoundingMode.HALF_UP);

            total = total.add(tax);
        }

        return total;
    }

    @Benchmark
    public BigDecimal scaledLongThenConvertAtBoundary() {
        // This version performs hot-loop arithmetic using scaled integers,
        // then converts to BigDecimal only at the API / domain boundary.
        //
        // This is often a good design when exact decimal representation is needed externally,
        // but high-throughput internal processing should avoid object churn.
        long totalTaxInCents = 0;

        for (int i = 0; i < SIZE; i++) {
            // HALF_UP for positive values: add half of the divisor before division.
            long taxInCents = (pricesInCents[i] * ratesBasisPoints[i] + 5_000) / 10_000;
            totalTaxInCents += taxInCents;
        }

        return BigDecimal.valueOf(totalTaxInCents, 2);
    }
}
