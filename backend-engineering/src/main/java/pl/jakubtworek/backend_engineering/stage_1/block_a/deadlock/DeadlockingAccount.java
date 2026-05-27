package pl.jakubtworek.backend_engineering.stage_1.block_a.deadlock;

public class DeadlockingAccount {

    // Account state; synchronization on methods also provides memory visibility
    private int balance;

    public DeadlockingAccount(int balance) {
        // Initialize account with a starting balance
        this.balance = balance;
    }

    // Synchronized on the account instance (this)
    // Only one thread can modify the balance of this account at a time
    public synchronized void deposit(int amount) {
        balance += amount;
    }

    // Uses the same monitor (this) as deposit()
    // Ensures withdrawals cannot run concurrently with other balance modifications
    public synchronized void withdraw(int amount) {
        balance -= amount;
    }

    // Not synchronized – reading balance is not protected here
    // In this example it is acceptable for demonstration purposes
    public int getBalance() {
        return balance;
    }

    /**
     * Transfers money from this account to another account.
     *
     * First locks the current account (this), then the target account (other).
     * If two threads perform transfers in opposite directions,
     * they may lock the accounts in different order.
     *
     * Example:
     * Thread A: acc1 -> acc2
     * Thread B: acc2 -> acc1
     *
     * Each thread holds one lock and waits for the other,
     * which creates a circular wait condition.
     */
    public void transfer(DeadlockingAccount other, int amount) {
        synchronized (this) {
            // Artificial delay to increase the chance of thread interleaving
            sleep(50);

            synchronized (other) {
                // Perform the actual balance updates while holding both locks
                this.withdraw(amount);
                other.deposit(amount);
            }
        }
    }

    // Helper method used only to simulate timing issues between threads
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}