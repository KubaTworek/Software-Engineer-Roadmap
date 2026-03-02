package pl.jakubtworek.backend_systems_lab_stage_1.block_a.deadlock;

import java.util.concurrent.*;

/**
 * Deadlock-free implementation using thread confinement.
 *
 * ------------------------------------------------------------
 * Strategy: Eliminate shared concurrency
 * ------------------------------------------------------------
 *
 * Instead of protecting shared state with locks,
 * we change the concurrency model entirely.
 *
 * All transfer operations are executed by a single thread.
 *
 * This guarantees:
 *
 *   - No concurrent access to account state
 *   - No need for locks
 *   - No possibility of deadlock
 *
 * ------------------------------------------------------------
 * Why deadlock is impossible here
 * ------------------------------------------------------------
 *
 * Deadlock requires:
 *   - Multiple threads
 *   - Each holding resources
 *   - Circular waiting
 *
 * Here:
 *
 *   - Only one worker thread performs mutations
 *   - No nested lock acquisition
 *   - No circular wait graph can form
 *
 * Therefore:
 *
 *   Coffman conditions are not satisfied.
 *
 * Specifically:
 *
 *   Mutual exclusion across multiple threads ❌
 *
 * There is no concurrent lock acquisition.
 *
 * ------------------------------------------------------------
 * Why future.get()?
 * ------------------------------------------------------------
 *
 * executor.submit() is asynchronous.
 *
 * Calling future.get():
 *   - Blocks caller until task completes
 *   - Establishes happens-before relationship
 *   - Guarantees visibility of state changes
 *
 * Without future.get(), caller might observe stale data.
 *
 * ------------------------------------------------------------
 * Trade-offs
 * ------------------------------------------------------------
 *
 * Pros:
 *   - No locks
 *   - No CAS
 *   - No deadlock risk
 *   - Simplified reasoning
 *
 * Cons:
 *   - Throughput limited to single thread
 *   - Potential bottleneck
 *   - Blocking call (get)
 *
 * This is essentially:
 *   - Actor model
 *   - Event loop model
 *
 * Scalable alternative:
 *   - Partition accounts across multiple single-thread executors
 *   - Shard by account id
 */
public class SingleThreadTransferService {

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public void transfer(AccountData from, AccountData to, int amount) {
        Future<?> future = executor.submit(() -> {
            from.balance -= amount;
            to.balance += amount;
        });

        try {
            future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class AccountData {
        int balance;

        public AccountData(int balance) {
            this.balance = balance;
        }

        public int getBalance() {
            return balance;
        }
    }
}