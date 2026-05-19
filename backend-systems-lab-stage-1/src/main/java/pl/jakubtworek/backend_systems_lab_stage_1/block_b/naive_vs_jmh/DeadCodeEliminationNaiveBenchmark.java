package pl.jakubtworek.backend_systems_lab_stage_1.block_b.naive_vs_jmh;

public final class DeadCodeEliminationNaiveBenchmark {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This benchmark is intentionally broken.
        //
        // The computed result is never used.
        // A sufficiently smart optimizer may remove some or all of the work.
        //
        // This demonstrates why benchmark code must make results observable.

        long start = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            BenchmarkTarget.compute(i);
        }

        long end = System.nanoTime();

        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }
}