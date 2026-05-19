package pl.jakubtworek.backend_systems_lab_stage_1.block_b.naive_vs_jmh;

public final class NaiveBenchmark {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This is an intentionally naive benchmark.
        //
        // Problems:
        // - no proper warmup,
        // - no fork isolation,
        // - no control over JIT compilation phases,
        // - no protection against dead-code elimination,
        // - no statistical analysis,
        // - results depend heavily on runtime conditions.
        //
        // This class is useful only as a demonstration of what NOT to trust blindly.

        long start = System.nanoTime();

        long result = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            result += BenchmarkTarget.compute(i);
        }

        long end = System.nanoTime();

        // Printing result prevents the whole loop from being obviously unused.
        // However, this still does not make the benchmark methodologically correct.
        System.out.println("Result: " + result);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }
}