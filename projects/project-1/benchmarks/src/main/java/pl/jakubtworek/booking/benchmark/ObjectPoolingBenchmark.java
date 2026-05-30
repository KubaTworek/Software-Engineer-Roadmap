package pl.jakubtworek.booking.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
public class ObjectPoolingBenchmark {
    static class MutableReservationView {
        long id;
        long eventId;
        int status;

        void reset(long id, long eventId, int status) {
            this.id = id;
            this.eventId = eventId;
            this.status = status;
        }
    }

    @State(Scope.Thread)
    public static class PoolState {
        ArrayDeque<MutableReservationView> pool = new ArrayDeque<>();

        @Setup(Level.Trial)
        public void setup() {
            for (int i = 0; i < 1024; i++) {
                pool.push(new MutableReservationView());
            }
        }
    }

    @Benchmark
    public void plainAllocation(Blackhole blackhole) {
        MutableReservationView view = new MutableReservationView();
        view.reset(1, 2, 3);
        blackhole.consume(view);
    }

    @Benchmark
    public void naiveObjectPool(PoolState state, Blackhole blackhole) {
        MutableReservationView view = state.pool.poll();
        if (view == null) {
            view = new MutableReservationView();
        }
        view.reset(1, 2, 3);
        blackhole.consume(view);
        state.pool.push(view);
    }
}
