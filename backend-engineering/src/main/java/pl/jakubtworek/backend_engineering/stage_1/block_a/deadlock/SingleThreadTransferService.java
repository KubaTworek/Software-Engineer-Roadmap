package pl.jakubtworek.backend_engineering.stage_1.block_a.deadlock;

import java.util.concurrent.*;

public class SingleThreadTransferService {

    // Executor with exactly one worker thread.
    // All submitted tasks are executed sequentially in the same thread.
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public void transfer(AccountData from, AccountData to, int amount) {

        // Submit transfer logic as a task to the single-thread executor.
        // The task will be executed in FIFO order relative to other transfers.
        Future<?> future = executor.submit(() -> {

            // Balance updates are performed inside the executor thread,
            // so no synchronization is required here.
            from.balance -= amount;
            to.balance += amount;
        });

        try {
            // Wait for the submitted task to finish before returning.
            // Ensures that the transfer has completed when this method exits.
            future.get();

        } catch (Exception e) {

            // Wrap checked exceptions from Future into unchecked exception
            // so the caller does not have to handle ExecutionException / InterruptedException.
            throw new RuntimeException(e);
        }
    }

    public static class AccountData {

        // Mutable state representing account balance
        int balance;

        public AccountData(int balance) {
            this.balance = balance;
        }

        // Simple read access to current balance
        public int getBalance() {
            return balance;
        }
    }
}