package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TicketStore implementation using atomic operations (CAS)
 * instead of blocking synchronization (synchronized / Lock).
 */
public class AtomicTicketStore implements TicketStore {

    // Number of tickets currently available.
    // AtomicInteger allows atomic updates without using locks.
    private final AtomicInteger available = new AtomicInteger(1);

    // Number of successfully sold tickets.
    // Incremented only after a successful CAS update.
    private final AtomicInteger sold = new AtomicInteger(0);

    // Initial number of tickets (useful for validation in tests or reporting).
    private final int initial = 1;

    @Override
    public void buy() {

        // Retry loop: compareAndSet may fail if another thread
        // modifies the value between get() and the CAS operation.
        while (true) {

            // Read the current number of available tickets.
            int current = available.get();

            // If no tickets are available, stop the purchase attempt.
            if (current <= 0) {
                return;
            }

            // Attempt to atomically decrement the available tickets.
            // This succeeds only if the value is still equal to 'current'.
            if (available.compareAndSet(current, current - 1)) {

                // CAS succeeded → this thread acquired the ticket.
                // Increase the sold counter.
                sold.incrementAndGet();

                return;
            }

            // If CAS failed, another thread updated the value first.
            // The loop retries with a fresh read.
        }
    }

    @Override
    public int getAvailable() {
        // AtomicInteger ensures visibility of the latest value across threads.
        return available.get();
    }

    @Override
    public int getSold() {
        // Returns the number of successfully sold tickets.
        return sold.get();
    }

    @Override
    public int getInitial() {
        // Returns the initial number of tickets configured for the store.
        return initial;
    }

    @Override
    public String name() {
        // Identifier used to distinguish this implementation in tests/benchmarks.
        return "Atomic (CAS)";
    }
}