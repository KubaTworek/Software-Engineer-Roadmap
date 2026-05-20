package pl.jakubtworek.backend_engineering.stage_2.block_a.test.application.testdouble;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.TransactionManager;

// Test double for transaction management.
// It executes the action immediately without opening a real database transaction.
public final class FakeTransactionManager implements TransactionManager {

    private int transactionCount;

    @Override
    public void executeInTransaction(Runnable action) {
        transactionCount++;
        action.run();
    }

    public int transactionCount() {
        return transactionCount;
    }
}