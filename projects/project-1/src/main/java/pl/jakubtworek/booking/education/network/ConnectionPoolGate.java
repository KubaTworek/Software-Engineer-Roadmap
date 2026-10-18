package pl.jakubtworek.booking.education.network;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Modeluje osobny timeout oczekiwania na połączenie z puli. Request może
 * wyczerpać deadline, zanim rozpocznie connect/read, jeżeli wszystkie istniejące
 * połączenia są zajęte.
 */
public final class ConnectionPoolGate {
    private final Semaphore permits;

    public ConnectionPoolGate(int poolSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be positive");
        }
        this.permits = new Semaphore(poolSize, true);
    }

    public Lease acquire(Duration timeout) throws InterruptedException {
        if (!permits.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
            throw new PoolAcquireTimeoutException("Connection pool wait exceeded " + timeout);
        }
        return new Lease();
    }

    public final class Lease implements AutoCloseable {
        private boolean closed;

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                permits.release();
            }
        }
    }

    public static final class PoolAcquireTimeoutException extends RuntimeException {
        public PoolAcquireTimeoutException(String message) {
            super(message);
        }
    }
}
