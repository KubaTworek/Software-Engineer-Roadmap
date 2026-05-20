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
public class MoneyRepresentationBenchmark {

    private static final int SIZE = 10_000;

    private BigDecimal[] bigDecimalAmounts;
    private MoneyAsScaledLong[] wrappedAmounts;
    private long[] primitiveCents;

    @Setup
    public void setup() {
        bigDecimalAmounts = new BigDecimal[SIZE];
        wrappedAmounts = new MoneyAsScaledLong[SIZE];
        primitiveCents = new long[SIZE];

        for (int i = 0; i < SIZE; i++) {
            long cents = 1_000 + i;

            bigDecimalAmounts[i] = BigDecimal.valueOf(cents, 2);
            wrappedAmounts[i] = MoneyAsScaledLong.ofCents(cents);
            primitiveCents[i] = cents;
        }
    }

    @Benchmark
    public BigDecimal sumBigDecimalAmounts() {
        // BigDecimal addition allocates a new BigDecimal for each intermediate result.
        BigDecimal total = BigDecimal.ZERO;

        for (BigDecimal amount : bigDecimalAmounts) {
            total = total.add(amount);
        }

        return total;
    }

    @Benchmark
    public MoneyAsScaledLong sumWrappedMoneyAmounts() {
        // This looks lighter than BigDecimal, but the immutable wrapper still allocates
        // a new MoneyAsScaledLong object for every plus() call.
        MoneyAsScaledLong total = MoneyAsScaledLong.ofCents(0);

        for (MoneyAsScaledLong amount : wrappedAmounts) {
            total = total.plus(amount);
        }

        return total;
    }

    @Benchmark
    public long sumPrimitiveCents() {
        // Primitive long arithmetic avoids object allocation in the hot loop.
        long total = 0;

        for (long cents : primitiveCents) {
            total += cents;
        }

        return total;
    }

    @Benchmark
    public BigDecimal sumPrimitiveCentsThenConvert() {
        // Convert to BigDecimal only once at the boundary.
        // This keeps the hot loop allocation-free while preserving decimal API output.
        long total = 0;

        for (long cents : primitiveCents) {
            total += cents;
        }

        return BigDecimal.valueOf(total, 2);
    }
}