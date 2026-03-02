package pl.jakubtworek.backend_systems_lab_stage_1.block_a.race_condition;

/**
 * Thread-safe implementation using intrinsic locking (monitor).
 *
 * What synchronized provides:
 *
 * 1. Mutual exclusion (atomicity at method level)
 *    Only one thread can execute buy() at a time.
 *
 * 2. Visibility guarantees (Java Memory Model)
 *    Entering a synchronized block establishes a happens-before
 *    relationship with the previous unlock of the same monitor.
 *
 *    This ensures:
 *      - Changes made by one thread are visible to others.
 *      - No stale reads.
 *
 * 3. Memory ordering
 *    Prevents reordering of reads/writes across the monitor boundary.
 *
 * Why method-level synchronized?
 *
 * Declaring the entire method synchronized ensures that:
 *  - The condition check
 *  - The decrement
 *  - The increment
 *
 * are executed as one atomic critical section.
 *
 * Trade-offs:
 *
 *  - Pros:
 *      - Simple
 *      - Safe
 *      - Clear intent
 *
 *  - Cons:
 *      - Blocks competing threads
 *      - Reduces scalability under high contention
 *      - Monitor acquisition has overhead
 *
 * This solution is appropriate when:
 *  - Critical section is small
 *  - Contention is limited
 *  - Simplicity is preferred over maximum throughput
 */
public class SynchronizedTicketStore implements TicketStore {

    private int available = 1;
    private final int initial = 1;
    private int sold = 0;

    @Override
    public synchronized void buy() {
        if (available > 0) {
            available--;
            sold++;
        }
    }

    @Override
    public int getAvailable() {
        return available;
    }

    @Override
    public int getSold() {
        return sold;
    }

    @Override
    public int getInitial() {
        return initial;
    }

    @Override
    public String name() {
        return "Synchronized";
    }
}