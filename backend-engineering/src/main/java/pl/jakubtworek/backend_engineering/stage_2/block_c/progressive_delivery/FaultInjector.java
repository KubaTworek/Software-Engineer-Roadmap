package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.util.function.Supplier;

/** Deterministic, bounded fault injection for tests and game-day scenarios. */
public final class FaultInjector {

    public static final class InjectedFaultException extends RuntimeException {
        public InjectedFaultException(String faultName) {
            super("injected fault: " + faultName);
        }
    }

    private final String faultName;
    private int remainingFailures;

    public FaultInjector(String faultName, int failureCount) {
        if (faultName == null || faultName.isBlank()) throw new IllegalArgumentException("faultName is required");
        if (failureCount < 0) throw new IllegalArgumentException("failureCount cannot be negative");
        this.faultName = faultName;
        this.remainingFailures = failureCount;
    }

    public synchronized <T> T execute(Supplier<T> operation) {
        if (remainingFailures > 0) {
            remainingFailures--;
            throw new InjectedFaultException(faultName);
        }
        return operation.get();
    }
}
