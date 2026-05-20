package pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh;

public final class BenchmarkTarget {

    private BenchmarkTarget() {
    }

    public static long compute(int input) {
        // This method is intentionally small and deterministic.
        // Naive benchmarks are especially fragile for this kind of code,
        // because the JIT may inline it, optimize it, or even remove unused work.
        long result = input;

        for (int i = 0; i < 100; i++) {
            result = result * 31 + i;
            result ^= (result >>> 13);
        }

        return result;
    }
}