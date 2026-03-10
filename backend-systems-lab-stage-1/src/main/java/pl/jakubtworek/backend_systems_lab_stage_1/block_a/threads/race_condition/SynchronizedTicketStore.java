package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

/**
 * TicketStore implementation using intrinsic locking (synchronized).
 * The monitor of the current object protects updates to shared state.
 */
public class SynchronizedTicketStore implements TicketStore {

    // Number of tickets currently available
    private int available = 1;

    // Initial number of tickets used for validation/reporting
    private final int initial = 1;

    // Number of successfully sold tickets
    private int sold = 0;

    @Override
    public synchronized void buy() {

        // Only one thread at a time can execute this method
        if (available > 0) {

            // Decrease the number of available tickets
            available--;

            // Record the successful sale
            sold++;
        }
    }

    @Override
    public int getAvailable() {
        // Returns the current number of available tickets
        return available;
    }

    @Override
    public int getSold() {
        // Returns the number of tickets recorded as sold
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
        return "Synchronized";
    }
}