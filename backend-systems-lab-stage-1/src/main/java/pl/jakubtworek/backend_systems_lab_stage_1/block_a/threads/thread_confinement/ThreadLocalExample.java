package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.thread_confinement;

/**
 * Demonstrates thread-local confinement.
 *
 * Each thread has its own independent copy of `counter`.
 *
 * ThreadLocal works by storing values in a per-thread map:
 *   Thread -> ThreadLocalMap -> (ThreadLocal, value)
 *
 * Therefore:
 *   - No shared state
 *   - No synchronization required
 *   - No visibility issues between threads
 *
 * Important:
 *   ThreadLocal is NOT shared memory.
 *   Each thread sees its own value.
 *
 * Common use cases:
 *   - request-scoped data
 *   - security context
 *   - transaction context
 *   - correlation IDs
 *
 * Pitfall:
 *   In thread pools, ThreadLocal values may leak
 *   if not cleared properly.
 */
public class ThreadLocalExample {

    private static final ThreadLocal<Integer> counter =
            ThreadLocal.withInitial(() -> 0);

    public void increment() {
        counter.set(counter.get() + 1);
    }

    public int get() {
        return counter.get();
    }
}