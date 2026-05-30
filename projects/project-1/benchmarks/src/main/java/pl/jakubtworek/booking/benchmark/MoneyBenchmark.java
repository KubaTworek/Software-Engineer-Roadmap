package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class MoneyBenchmark {
    @Param({"1000", "100000"})
    int operations;

    @Benchmark
    public void bigDecimalMoney(Blackhole blackhole) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < operations; i++) {
            sum = sum.add(BigDecimal.valueOf(i).movePointLeft(2));
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void longMinorUnits(Blackhole blackhole) {
        long cents = 0;
        for (int i = 0; i < operations; i++) {
            cents += i;
        }
        blackhole.consume(cents);
    }
}
