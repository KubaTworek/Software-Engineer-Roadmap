package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

import java.util.concurrent.*;

/**
 * TicketStore implementation where all state modifications
 * are executed inside a single-thread executor.
 */
public class SingleThreadTicketStore implements TicketStore {

    // Number of tickets currently available
    private int available = 1;

    // Number of sold tickets
    private int sold = 0;

    // Initial number of tickets used for validation/reporting
    private final int initial = 1;

    // Executor that processes tasks sequentially on a single worker thread
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    public void buy() {

        // Submit the purchase operation to the single-thread executor
        Future<?> future = executor.submit(() -> {

            // This code runs on the executor's worker thread
            if (available > 0) {

                // Decrease number of available tickets
                available--;

                // Record the successful sale
                sold++;
            }
        });

        try {
            // Wait for the task to complete before returning
            future.get();
        } catch (Exception e) {

            // Wrap checked exceptions into RuntimeException
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getAvailable() {
        // Returns the current number of available tickets
        return available;
    }

    @Override
    public int getSold() {
        // Returns number of tickets recorded as sold
        return sold;
    }

    @Override
    public int getInitial() {
        // Returns the initial number of tickets
        return initial;
    }

    @Override
    public String name() {
        // Identifier used to distinguish this implementation in tests
        return "Single-thread Executor";
    }
}