package pl.jakubtworek.backend_systems_lab_stage_1.block_a.deadlock;

public class DeadlockingAccount {

    private int balance;

    public DeadlockingAccount(int balance) {
        this.balance = balance;
    }

    public synchronized void deposit(int amount) {
        balance += amount;
    }

    public synchronized void withdraw(int amount) {
        balance -= amount;
    }

    public int getBalance() {
        return balance;
    }

    /**
     * Classical lock ordering problem:
     *
     * Thread A: locks acc1 -> waits for acc2
     * Thread B: locks acc2 -> waits for acc1
     *
     * Circular wait → deadlock
     */
    public void transfer(DeadlockingAccount other, int amount) {
        synchronized (this) {
            sleep(50); // zwiększa prawdopodobieństwo przeplotu
            synchronized (other) {
                this.withdraw(amount);
                other.deposit(amount);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}