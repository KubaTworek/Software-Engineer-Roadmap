package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

/**
 * Deliberately incorrect implementation used to demonstrate a race condition.
 * Multiple threads can pass the same check and modify shared state concurrently.
 */
public class BrokenTicketStore implements TicketStore {

    // Number of tickets currently available (not protected by any synchronization)
    private int available = 1;

    // Initial number of tickets used for validation or reporting
    private final int initial = 1;

    // Counter of sold tickets (also not thread-safe)
    private int sold = 0;

    @Override
    public void buy() {

        // Check if at least one ticket is available
        if (available > 0) {

            // Local copy of the current value
            int tmp = available;

            // Hint to the scheduler to switch threads here,
            // increasing the probability of a race condition
            Thread.yield();

            // Write back the decremented value
            available = tmp - 1;

            // Increase number of sold tickets
            sold++;
        }
    }

    @Override
    public int getAvailable() {
        // Returns current number of available tickets
        return available;
    }

    @Override
    public int getSold() {
        // Returns number of tickets recorded as sold
        return sold;
    }

    @Override
    public int getInitial() {
        // Returns initial number of tickets
        return initial;
    }

    @Override
    public String name() {
        // Label used to identify this implementation in tests
        return "Broken (Lost Update)";
    }
}