package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class PolymorphismBenchmark {
    interface PricePolicy {
        long apply(long cents);
    }

    static class RegularPolicy implements PricePolicy {
        public long apply(long cents) { return cents; }
    }

    static class VipPolicy implements PricePolicy {
        public long apply(long cents) { return cents * 90 / 100; }
    }

    static class StudentPolicy implements PricePolicy {
        public long apply(long cents) { return cents * 80 / 100; }
    }

    @State(Scope.Thread)
    public static class Data {
        @Param({"1000", "100000"})
        int size;
        PricePolicy monomorphic = new RegularPolicy();
        PricePolicy[] megamorphic;

        @Setup(Level.Trial)
        public void setup() {
            megamorphic = new PricePolicy[size];
            PricePolicy[] policies = {new RegularPolicy(), new VipPolicy(), new StudentPolicy()};
            for (int i = 0; i < size; i++) {
                megamorphic[i] = policies[i % policies.length];
            }
        }
    }

    @Benchmark
    public void monomorphicCallSite(Data data, Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < data.size; i++) {
            sum += data.monomorphic.apply(1000);
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void megamorphicCallSite(Data data, Blackhole blackhole) {
        long sum = 0;
        for (PricePolicy policy : data.megamorphic) {
            sum += policy.apply(1000);
        }
        blackhole.consume(sum);
    }
}
