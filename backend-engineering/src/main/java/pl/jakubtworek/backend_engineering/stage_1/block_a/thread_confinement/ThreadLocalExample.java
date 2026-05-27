package pl.jakubtworek.backend_engineering.stage_1.block_a.thread_confinement;

/**
 * Example of thread-local state.
 *
 * The counter is not shared between threads.
 * Each thread that accesses this class gets its own value.
 */
public class ThreadLocalExample {

    /**
     * Thread-local counter.
     *
     * Every thread accessing this variable has an independent
     * copy stored inside the ThreadLocal mechanism.
     *
     * The initial value for each thread is 0.
     */
    private static final ThreadLocal<Integer> counter =
            ThreadLocal.withInitial(() -> 0);

    /**
     * Increments the counter for the CURRENT thread.
     *
     * Steps:
     * 1. Read the value associated with the current thread.
     * 2. Increment it.
     * 3. Store the updated value back in ThreadLocal.
     *
     * This operation does not affect counters of other threads.
     */
    public void increment() {
        counter.set(counter.get() + 1);
    }

    /**
     * Returns the counter value for the CURRENT thread.
     *
     * If another thread calls this method,
     * it will see its own independent value.
     */
    public int get() {
        return counter.get();
    }
}