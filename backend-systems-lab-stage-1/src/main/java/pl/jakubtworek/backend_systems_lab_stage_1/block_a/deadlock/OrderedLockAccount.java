package pl.jakubtworek.backend_systems_lab_stage_1.block_a.deadlock;

/**
 * Deadlock-safe implementation using global lock ordering.
 *
 * ------------------------------------------------------------
 * Strategy: Global Lock Ordering
 * ------------------------------------------------------------
 *
 * To prevent deadlock, we break the "circular wait" condition.
 *
 * We impose a strict global ordering rule:
 *
 *   Always lock accounts in ascending order of id.
 *
 * This ensures:
 *
 *   Every thread acquires locks in the same order.
 *
 * Therefore:
 *
 *   Cyclic waiting becomes impossible.
 *
 * If all threads follow the same ordering rule,
 * a cycle in the wait-for graph cannot form.
 *
 * ------------------------------------------------------------
 * How it works
 * ------------------------------------------------------------
 *
 * Step 1:
 *   Determine which account has smaller id.
 *
 * Step 2:
 *   Lock smaller one first.
 *
 * Step 3:
 *   Lock larger one second.
 *
 * Since every thread uses the same comparison rule,
 * lock acquisition order is globally consistent.
 *
 * ------------------------------------------------------------
 * Why this prevents deadlock
 * ------------------------------------------------------------
 *
 * Assume two accounts:
 *   A (id=1)
 *   B (id=2)
 *
 * Regardless of transfer direction:
 *
 *   transfer(A → B)
 *   transfer(B → A)
 *
 * Both threads will:
 *
 *   lock A first
 *   then lock B
 *
 * No circular dependency can arise.
 *
 * We eliminate Coffman's condition #4:
 *
 *   Circular wait ❌
 *
 * ------------------------------------------------------------
 * Memory Model & Visibility
 * ------------------------------------------------------------
 *
 * synchronized(first) {
 *   synchronized(second) {
 *       ...
 *   }
 * }
 *
 * synchronized provides:
 *
 *   - Mutual exclusion
 *   - Happens-before relationship
 *   - Visibility guarantees
 *
 * unlock(first/second) happens-before
 * next lock acquisition of the same monitor.
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - Simple
 *   - Deterministic
 *   - No retries needed
 *   - No timeouts
 *
 * Cons:
 *   - Requires global ordering rule
 *   - All resources must be comparable
 *   - Must be applied consistently across entire system
 *
 * If one place violates ordering rule,
 * deadlock can still occur.
 *
 * ------------------------------------------------------------
 * Architectural insight
 * ------------------------------------------------------------
 *
 * Deadlock is not random.
 * It is a graph cycle problem.
 *
 * By imposing total ordering on resources,
 * we eliminate possibility of cycle formation.
 *
 * This is one of the most robust and scalable
 * deadlock prevention strategies in multi-resource systems.
 */
public class OrderedLockAccount {

    private final int id;
    private int balance;

    public OrderedLockAccount(int id, int balance) {
        this.id = id;
        this.balance = balance;
    }

    public void transfer(OrderedLockAccount other, int amount) {

        OrderedLockAccount first =
                this.id < other.id ? this : other;

        OrderedLockAccount second =
                this.id < other.id ? other : this;

        synchronized (first) {
            synchronized (second) {
                this.balance -= amount;
                other.balance += amount;
            }
        }
    }

    public int getBalance() {
        return balance;
    }
}