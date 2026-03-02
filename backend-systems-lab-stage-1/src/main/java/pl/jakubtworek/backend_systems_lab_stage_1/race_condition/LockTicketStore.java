package pl.jakubtworek.backend_systems_lab_stage_1.race_condition;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe implementation using explicit locking via ReentrantLock.
 *
 * Why ReentrantLock?
 *
 * Similar to synchronized, we need to protect a critical section that:
 *   - checks available
 *   - modifies available
 *   - increments sold
 *
 * The invariant:
 *
 *   available + sold == initial
 *
 * must always hold.
 *
 * Instead of using intrinsic locking (synchronized),
 * we use explicit locking from java.util.concurrent.locks.
 *
 * ------------------------------------------------------------
 * What is ReentrantLock?
 * ------------------------------------------------------------
 *
 * ReentrantLock is an explicit mutual exclusion lock
 * with the same basic memory semantics as synchronized,
 * but with more advanced features.
 *
 * "Reentrant" means:
 *
 *   The same thread can acquire the same lock multiple times
 *   without deadlocking itself.
 *
 * Internally:
 *   - The lock keeps track of the owning thread.
 *   - It maintains a hold count.
 *
 * ------------------------------------------------------------
 * What guarantees does lock()/unlock() provide?
 * ------------------------------------------------------------
 *
 * 1. Mutual exclusion
 *    Only one thread can execute the critical section at a time.
 *
 * 2. Visibility guarantees (Java Memory Model)
 *    A successful unlock() happens-before
 *    a subsequent lock() on the same lock.
 *
 *    This ensures:
 *      - No stale reads
 *      - Writes inside the critical section
 *        become visible to other threads
 *
 * 3. Memory ordering
 *    Similar to synchronized:
 *      - No instruction reordering across lock boundaries.
 *
 * ------------------------------------------------------------
 * Why try/finally?
 * ------------------------------------------------------------
 *
 * Always release the lock in finally block.
 *
 * If unlock() is not called:
 *   - Other threads will block forever.
 *   - You introduce a deadlock.
 *
 * This is one of the risks of manual locking.
 *
 * ------------------------------------------------------------
 * Why not synchronized?
 * ------------------------------------------------------------
 *
 * synchronized:
 *   - Simpler
 *   - Implicit monitor management
 *   - Less error-prone
 *
 * ReentrantLock:
 *   - More control
 *   - Can use tryLock()
 *   - Can use interruptible locking
 *   - Can configure fairness policy
 *
 * Example advanced features:
 *   lock.tryLock()
 *   lock.lockInterruptibly()
 *   new ReentrantLock(true) // fair lock
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - More flexible than synchronized
 *   - Explicit control over locking strategy
 *   - Useful in complex coordination scenarios
 *
 * Cons:
 *   - More verbose
 *   - Easier to misuse (forget unlock)
 *   - No automatic scope-based release
 *
 * Performance:
 *   Under low contention, similar to synchronized.
 *   Under high contention, performance depends on lock configuration.
 *
 * Appropriate when:
 *   - You need advanced lock features
 *   - You need non-blocking attempt (tryLock)
 *   - You need interruptible lock acquisition
 *
 * For simple mutual exclusion, synchronized is often sufficient.
 */
public class LockTicketStore implements TicketStore {

    private int available = 1;
    private int sold = 0;
    private final int initial = 1;
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void buy() {
        lock.lock();
        try {
            if (available > 0) {
                available--;
                sold++;
            }
        } finally {
            lock.unlock();
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
        return "ReentrantLock";
    }
}