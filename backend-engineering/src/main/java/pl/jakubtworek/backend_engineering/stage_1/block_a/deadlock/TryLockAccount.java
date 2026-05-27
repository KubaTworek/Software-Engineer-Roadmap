package pl.jakubtworek.backend_engineering.stage_1.block_a.deadlock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockAccount {

    // Unique account identifier (not used in locking logic here,
    // but useful for debugging or logging transfers)
    private final int id;

    // Current account balance
    private int balance;

    // Explicit lock protecting access to the account state
    private final ReentrantLock lock = new ReentrantLock();

    public TryLockAccount(int id, int balance) {
        this.id = id;
        this.balance = balance;
    }

    public void transfer(TryLockAccount other, int amount) {

        // Retry loop – the operation repeats until both locks are acquired
        while (true) {

            // Track which locks were successfully acquired
            boolean gotThis = false;
            boolean gotOther = false;

            try {
                // Attempt to acquire this account's lock with timeout
                gotThis = lock.tryLock(100, TimeUnit.MILLISECONDS);

                // Attempt to acquire the other account's lock
                gotOther = other.lock.tryLock(100, TimeUnit.MILLISECONDS);

                // Only perform the transfer if both locks were acquired
                if (gotThis && gotOther) {
                    this.balance -= amount;
                    other.balance += amount;
                    return;
                }

            } catch (InterruptedException ignored) {
                // Interrupted during tryLock timeout wait
            } finally {

                // Release locks that were successfully acquired
                // to avoid holding partial resources before retrying
                if (gotThis) lock.unlock();
                if (gotOther) other.lock.unlock();
            }

            // If both locks were not obtained, retry the entire operation
        }
    }

    // Balance read is not locked here; correctness relies on transfers
    // modifying balance only while holding the account lock
    public int getBalance() {
        return balance;
    }
}