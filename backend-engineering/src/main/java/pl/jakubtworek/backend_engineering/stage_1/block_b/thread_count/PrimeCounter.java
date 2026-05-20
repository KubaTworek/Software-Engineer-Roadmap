package pl.jakubtworek.backend_engineering.stage_1.block_b.thread_count;

public final class PrimeCounter {

    private PrimeCounter() {
    }

    public static int countPrimes(int maxExclusive) {
        // This intentionally uses a simple CPU-heavy algorithm.
        // The goal is to create measurable CPU work, not optimal prime counting.
        int count = 0;

        for (int candidate = 2; candidate < maxExclusive; candidate++) {
            if (isPrime(candidate)) {
                count++;
            }
        }

        return count;
    }

    private static boolean isPrime(int value) {
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