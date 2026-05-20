package pl.jakubtworek.backend_engineering.stage_1.block_b.g1_vs_zgc;

import java.util.Arrays;

public final class LatencyProbe implements Runnable {

    private static final int HISTOGRAM_SIZE = 100_000;

    private final WorkloadConfig config;
    private final long[] observedDelaysMicros = new long[HISTOGRAM_SIZE];

    private volatile boolean running = true;
    private int index = 0;

    public LatencyProbe(WorkloadConfig config) {
        this.config = config;
    }

    @Override
    public void run() {
        // This thread measures scheduling delay.
        // It is not a perfect latency benchmark, but it helps reveal long JVM pauses.
        //
        // If GC causes stop-the-world pauses, this thread will wake up late.
        while (running && index < observedDelaysMicros.length) {
            long before = System.nanoTime();

            try {
                Thread.sleep(config.latencyProbeSleepMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            long after = System.nanoTime();

            long expectedMicros = config.latencyProbeSleepMillis() * 1000L;
            long actualMicros = (after - before) / 1000L;
            long delayMicros = Math.max(0, actualMicros - expectedMicros);

            observedDelaysMicros[index++] = delayMicros;
        }

        printSummary();
    }

    private void printSummary() {
        long[] copy = Arrays.copyOf(observedDelaysMicros, index);
        Arrays.sort(copy);

        if (copy.length == 0) {
            return;
        }

        System.out.println("Latency probe summary:");
        System.out.println("p50 delay micros: " + percentile(copy, 50));
        System.out.println("p90 delay micros: " + percentile(copy, 90));
        System.out.println("p99 delay micros: " + percentile(copy, 99));
        System.out.println("max delay micros: " + copy[copy.length - 1]);
    }

    private long percentile(long[] values, int percentile) {
        int position = Math.min(values.length - 1, (values.length * percentile) / 100);
        return values[position];
    }

    public void stop() {
        running = false;
    }
}