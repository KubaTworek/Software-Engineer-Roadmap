package pl.jakubtworek.backend_systems_lab_stage_1.block_a.deadlock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Deadlock-safe implementation using tryLock with timeout and retry.
 *
 * ------------------------------------------------------------
 * Strategy: Break "Hold and Wait"
 * ------------------------------------------------------------
 *
 * Instead of blocking indefinitely when acquiring a lock,
 * we attempt to acquire it with timeout.
 *
 * If both locks are not acquired successfully:
 *
 *   - We release any acquired lock
 *   - Retry the entire operation
 *
 * This prevents a thread from:
 *
 *   holding one resource while waiting forever for another.
 *
 * Therefore:
 *
 *   Coffman condition #2 (Hold and Wait) is broken.
 *
 * No deadlock can occur.
 *
 * ------------------------------------------------------------
 * How it works
 * ------------------------------------------------------------
 *
 * 1. Try to acquire first lock (with timeout).
 * 2. Try to acquire second lock (with timeout).
 * 3. If both acquired:
 *        perform transfer.
 *    Else:
 *        release any acquired lock and retry.
 *
 * Important:
 *
 * The locks are always released in finally block,
 * even if acquisition of the second lock fails.
 *
 * ------------------------------------------------------------
 * Why tryLock with timeout?
 * ------------------------------------------------------------
 *
 * lock.lock() blocks indefinitely.
 * tryLock(timeout) allows:
 *
 *   - Backoff strategy
 *   - Avoiding permanent circular waiting
 *   - Interruptible behavior
 *
 * This makes the system more resilient under contention.
 *
 * ------------------------------------------------------------
 * Memory Model Guarantees
 * ------------------------------------------------------------
 *
 * ReentrantLock provides the same happens-before guarantees
 * as synchronized:
 *
 *   unlock() happens-before
 *   subsequent successful lock() on same lock.
 *
 * Therefore:
 *   - Visibility is guaranteed
 *   - No stale reads
 *   - Proper ordering of writes
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - No deadlock
 *   - Flexible
 *   - Supports interruptible locking
 *   - Works without global ordering rule
 *
 * Cons:
 *   - More complex
 *   - Possible live-lock (threads repeatedly retry)
 *   - Increased CPU usage under heavy contention
 *   - Starvation possible
 *
 * ------------------------------------------------------------
 * Deadlock vs Livelock
 * ------------------------------------------------------------
 *
 * Deadlock:
 *   Threads blocked forever.
 *
 * Livelock:
 *   Threads not blocked,
 *   but repeatedly retry and make no progress.
 *
 * This approach eliminates deadlock,
 * but may introduce livelock if contention is extreme.
 *
 * Backoff strategies (e.g. random sleep)
 * can reduce livelock probability.
 *
 * ------------------------------------------------------------
 * Architectural Insight
 * ------------------------------------------------------------
 *
 * This strategy prevents deadlock dynamically,
 * rather than structurally (like global ordering).
 *
 * It is useful when:
 *   - Resource ordering is not easily definable
 *   - Resources are dynamic
 *   - Flexibility is needed
 */
public class TryLockAccount {

    private final int id;
    private int balance;
    private final ReentrantLock lock = new ReentrantLock();

    public TryLockAccount(int id, int balance) {
        this.id = id;
        this.balance = balance;
    }

    public void transfer(TryLockAccount other, int amount) {

        while (true) {
            boolean gotThis = false;
            boolean gotOther = false;

            try {
                gotThis = lock.tryLock(100, TimeUnit.MILLISECONDS);
                gotOther = other.lock.tryLock(100, TimeUnit.MILLISECONDS);

                if (gotThis && gotOther) {
                    this.balance -= amount;
                    other.balance += amount;
                    return;
                }

            } catch (InterruptedException ignored) {
            } finally {
                if (gotThis) lock.unlock();
                if (gotOther) other.lock.unlock();
            }

            // retry
        }
    }

    public int getBalance() {
        return balance;
    }
}