package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deliberately incorrect implementation used to demonstrate a race condition.
 * Multiple threads can pass the same check and modify shared state concurrently.
 */
public class BrokenTicketStore implements TicketStore {

    // Number of tickets currently available (not protected by any synchronization)
    private int available = 1;

    // Initial number of tickets used for validation or reporting
    private final int initial = 1;

    // Atomic only so the example isolates the broken check-then-act operation.
    private final AtomicInteger sold = new AtomicInteger();

    private final Runnable afterAvailabilityRead;

    public BrokenTicketStore() {
        this(Thread::yield);
    }

    /** Allows a test to force both buyers past the availability check. */
    public BrokenTicketStore(Runnable afterAvailabilityRead) {
        this.afterAvailabilityRead = Objects.requireNonNull(
                afterAvailabilityRead, "afterAvailabilityRead must not be null");
    }

    @Override
    public void buy() {

        // Check if at least one ticket is available
        if (available > 0) {

            // Local copy of the current value
            int tmp = available;

            afterAvailabilityRead.run();

            // Write back the decremented value
            available = tmp - 1;

            // Increase number of sold tickets
            sold.incrementAndGet();
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
        return sold.get();
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
