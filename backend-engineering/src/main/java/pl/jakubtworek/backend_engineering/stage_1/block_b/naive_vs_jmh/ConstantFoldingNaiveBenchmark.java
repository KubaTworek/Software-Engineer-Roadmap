package pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh;

public final class ConstantFoldingNaiveBenchmark {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This benchmark is intentionally misleading.
        //
        // The input is constant.
        // The JIT may be able to optimize the computation much more aggressively
        // than it would for realistic runtime-varying inputs.

        long start = System.nanoTime();

        long result = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            result += BenchmarkTarget.compute(42);
        }

        long end = System.nanoTime();

        System.out.println("Result: " + result);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }
}