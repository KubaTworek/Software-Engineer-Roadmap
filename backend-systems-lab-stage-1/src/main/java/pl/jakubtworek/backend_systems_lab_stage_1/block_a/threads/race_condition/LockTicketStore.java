package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

import java.util.concurrent.locks.ReentrantLock;

/**
 * TicketStore implementation using explicit locking with ReentrantLock.
 * The lock protects modifications of shared state: available and sold.
 */
public class LockTicketStore implements TicketStore {

    // Number of tickets currently available
    private int available = 1;

    // Number of sold tickets
    private int sold = 0;

    // Initial number of tickets used for verification/reporting
    private final int initial = 1;

    // Explicit mutual exclusion lock protecting the critical section
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void buy() {

        // Acquire the lock before accessing shared mutable state
        lock.lock();

        try {

            // Only one thread at a time can execute this block
            if (available > 0) {

                // Decrease number of available tickets
                available--;

                // Record the successful sale
                sold++;
            }

        } finally {

            // Always release the lock to avoid blocking other threads
            lock.unlock();
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
        // Returns the initial number of tickets
        return initial;
    }

    @Override
    public String name() {
        // Name used to identify this implementation in tests
        return "ReentrantLock";
    }
}