package pl.jakubtworek.backend_systems_lab_stage_1.block_b.cpu_vs_io;

public final class PrimeCalculator {

    private PrimeCalculator() {
    }

    public static int countPrimes(int maxExclusive) {
        // This intentionally uses a simple CPU-heavy algorithm.
        // The goal is not algorithmic efficiency.
        // The goal is to create a visible CPU hotspot for profiling.
        int count = 0;

        for (int candidate = 2; candidate < maxExclusive; candidate++) {
            if (isPrime(candidate)) {
                count++;
            }
        }

        return count;
    }

    private static boolean isPrime(int value) {
        // Trial division creates predictable CPU work.
        // This method should appear as a hot method in CPU profiling.
        if (value < 2) {
            return false;
        }

        for (int divisor = 2; divisor * divisor <= value; divisor++) {
            if (value % divisor == 0) {
                return false;
            }
        }

        return true;
    }
}