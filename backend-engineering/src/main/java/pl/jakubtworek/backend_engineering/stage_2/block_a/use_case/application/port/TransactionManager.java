package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port;

// Transaction boundary abstraction.
// The application service can define atomicity without depending on Spring transactions.
public interface TransactionManager {

    void executeInTransaction(Runnable action);
}