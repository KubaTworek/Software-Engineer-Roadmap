package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * CPU work is intentionally executor-agnostic. Changing the thread type must
 * not change the result, and a unit test must not claim a speedup from timing
 * noise. Throughput remains bounded by available CPU and is measured in JMH.
 */
public final class CpuBoundWorkload {

    public List<Integer> countPrimes(ExecutorService executor, List<Integer> upperBounds)
            throws InterruptedException {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(upperBounds, "upperBounds must not be null");
        List<Future<Integer>> futures = new ArrayList<>();
        for (Integer upperBound : upperBounds) {
            if (upperBound == null || upperBound < 0) {
                throw new IllegalArgumentException("upper bounds must be non-negative");
            }
            futures.add(executor.submit(() -> countPrimesUpTo(upperBound)));
        }

        List<Integer> results = new ArrayList<>(futures.size());
        for (Future<Integer> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException exception) {
                throw new IllegalStateException("CPU task failed", exception.getCause());
            }
        }
        return List.copyOf(results);
    }

    static int countPrimesUpTo(int upperBound) {
        int primes = 0;
        for (int candidate = 2; candidate <= upperBound; candidate++) {
            if (isPrime(candidate)) {
                primes++;
            }
        }
        return primes;
    }

    private static boolean isPrime(int candidate) {
        for (int divisor = 2; divisor * divisor <= candidate; divisor++) {
            if (candidate % divisor == 0) {
                return false;
            }
        }
        return true;
    }
}
