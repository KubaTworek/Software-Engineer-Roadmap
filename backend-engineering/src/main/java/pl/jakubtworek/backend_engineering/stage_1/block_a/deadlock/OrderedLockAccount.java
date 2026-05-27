package pl.jakubtworek.backend_engineering.stage_1.block_a.deadlock;

public class OrderedLockAccount {

    // Unique identifier used to determine global lock ordering
    private final int id;

    // Current account balance
    private int balance;

    public OrderedLockAccount(int id, int balance) {
        // id must remain immutable to guarantee consistent ordering
        this.id = id;
        this.balance = balance;
    }

    public void transfer(OrderedLockAccount other, int amount) {

        // Determine which account should be locked first
        // The account with the smaller id is always locked first
        OrderedLockAccount first =
                this.id < other.id ? this : other;

        // The account with the larger id is locked second
        OrderedLockAccount second =
                this.id < other.id ? other : this;

        // Acquire locks in deterministic order
        // Every thread will lock accounts using the same rule
        synchronized (first) {

            // Second lock is acquired only after the first one
            synchronized (second) {

                // Perform the transfer while holding both locks
                this.balance -= amount;
                other.balance += amount;
            }
        }
    }

    // Simple getter – not synchronized because balance updates
    // occur only while holding the account lock during transfers
    public int getBalance() {
        return balance;
    }
}