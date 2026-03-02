package pl.jakubtworek.backend_systems_lab_stage_1.race_condition;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe implementation using lock-free atomic operations (CAS).
 *
 * Why AtomicInteger?
 *
 * We want to ensure correctness of shared mutable state:
 *   - available
 *   - sold
 *
 * without using blocking synchronization (like synchronized or Lock).
 *
 * Instead of mutual exclusion, we use lock-free atomic operations
 * based on Compare-And-Set (CAS).
 *
 * ------------------------------------------------------------
 * What is CAS (Compare-And-Set)?
 * ------------------------------------------------------------
 *
 * CAS is a low-level CPU instruction (e.g., cmpxchg) exposed by JVM.
 *
 * Conceptually:
 *
 *   if (value == expected) {
 *       value = newValue;
 *       return true;
 *   } else {
 *       return false;
 *   }
 *
 * This entire operation is atomic at the hardware level.
 *
 * ------------------------------------------------------------
 * Why the retry loop?
 * ------------------------------------------------------------
 *
 * The buy() method uses a classical lock-free retry pattern:
 *
 *   1. Read current value
 *   2. Check condition
 *   3. Try to update via compareAndSet
 *   4. If CAS fails → retry
 *
 * CAS can fail when:
 *   - Another thread updated the value between get() and compareAndSet()
 *
 * Instead of blocking, we retry.
 *
 * This is called optimistic concurrency.
 *
 * ------------------------------------------------------------
 * What guarantees does AtomicInteger provide?
 * ------------------------------------------------------------
 *
 * 1. Atomicity (for single variable operations)
 *    compareAndSet() is atomic.
 *
 * 2. Visibility
 *    Atomic variables have volatile semantics.
 *    All writes are immediately visible to other threads.
 *
 * 3. Ordering
 *    Atomic operations create happens-before relationships
 *    similar to volatile writes/reads.
 *
 * ------------------------------------------------------------
 * Why not simple decrementAndGet()?
 * ------------------------------------------------------------
 *
 * Because we have a check-then-act logic:
 *
 *   if (available > 0) decrement
 *
 * A naive:
 *
 *   if (available.get() > 0)
 *       available.decrementAndGet();
 *
 * would reintroduce race condition.
 *
 * Therefore we use CAS to combine:
 *   - validation
 *   - update
 *
 * into one atomic step.
 *
 * ------------------------------------------------------------
 * Lock-free vs synchronized
 * ------------------------------------------------------------
 *
 * synchronized:
 *   - Blocking
 *   - Mutual exclusion
 *   - Simpler reasoning
 *
 * Atomic (CAS):
 *   - Non-blocking
 *   - Higher throughput under low contention
 *   - May spin under heavy contention
 *
 * CAS-based algorithms scale better,
 * but can degrade under extreme contention due to retry loops.
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - Non-blocking
 *   - High scalability
 *   - No monitor overhead
 *
 * Cons:
 *   - More complex logic
 *   - Possible CPU spinning
 *   - Harder to reason about in complex invariants
 *
 * This solution is appropriate when:
 *   - Critical section is small
 *   - Contention is moderate
 *   - High throughput is desired
 */
public class AtomicTicketStore implements TicketStore {

    private final AtomicInteger available = new AtomicInteger(1);
    private final AtomicInteger sold = new AtomicInteger(0);
    private final int initial = 1;

    @Override
    public void buy() {
        while (true) {
            int current = available.get();

            if (current <= 0) {
                return;
            }

            if (available.compareAndSet(current, current - 1)) {
                sold.incrementAndGet();
                return;
            }
        }
    }

    @Override
    public int getAvailable() {
        return available.get();
    }

    @Override
    public int getSold() {
        return sold.get();
    }

    @Override
    public int getInitial() {
        return initial;
    }

    @Override
    public String name() {
        return "Atomic (CAS)";
    }
}