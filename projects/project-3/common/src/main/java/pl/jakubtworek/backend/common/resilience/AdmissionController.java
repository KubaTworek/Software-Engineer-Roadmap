package pl.jakubtworek.backend.common.resilience;

import java.util.concurrent.Semaphore;

/**
 * Minimalny bulkhead oparty na limicie współbieżności.
 * Gdy zależność zwalnia, dokładanie wątków jedynie powiększa kolejkę i latency. Ten model
 * natychmiast odrzuca nadmiar pracy, pozostawiając zasoby dla żądań już przyjętych.
 */
public final class AdmissionController {
    private final Semaphore permits;

    public AdmissionController(int maximumConcurrentCalls) {
        this.permits = new Semaphore(maximumConcurrentCalls);
    }

    public Permit tryAcquire() {
        if (!permits.tryAcquire()) throw new LoadShedException();
        return new Permit();
    }

    public final class Permit implements AutoCloseable {
        private boolean released;
        @Override public void close() {
            if (!released) {
                released = true;
                permits.release();
            }
        }
    }

    public static final class LoadShedException extends RuntimeException { }
}
