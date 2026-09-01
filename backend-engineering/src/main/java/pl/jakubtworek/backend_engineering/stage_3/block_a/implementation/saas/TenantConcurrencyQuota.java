package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

/** Per-tenant bulkhead: a noisy tenant cannot consume another tenant's permits. */
public final class TenantConcurrencyQuota {

    private final int permitsPerTenant;
    private final Map<TenantId, TenantState> states = new ConcurrentHashMap<>();

    public TenantConcurrencyQuota(int permitsPerTenant) {
        if (permitsPerTenant < 1) {
            throw new IllegalArgumentException("permitsPerTenant must be positive");
        }
        this.permitsPerTenant = permitsPerTenant;
    }

    public Permit tryAcquire(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        TenantState state = states.computeIfAbsent(tenantId, ignored -> new TenantState(permitsPerTenant));
        if (!state.semaphore.tryAcquire()) {
            state.rejected.increment();
            throw new TenantQuotaExceededException(tenantId);
        }
        state.accepted.increment();
        return new Permit(state.semaphore);
    }

    public Snapshot snapshot(TenantId tenantId) {
        TenantState state = states.computeIfAbsent(tenantId, ignored -> new TenantState(permitsPerTenant));
        return new Snapshot(
                permitsPerTenant - state.semaphore.availablePermits(),
                state.accepted.sum(),
                state.rejected.sum());
    }

    private static final class TenantState {
        private final Semaphore semaphore;
        private final LongAdder accepted = new LongAdder();
        private final LongAdder rejected = new LongAdder();

        private TenantState(int permits) {
            this.semaphore = new Semaphore(permits, true);
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }

    public record Snapshot(int active, long accepted, long rejected) {
    }

    public static final class TenantQuotaExceededException extends RuntimeException {
        public TenantQuotaExceededException(TenantId tenantId) {
            super("concurrency quota exceeded for tenant " + tenantId.value());
        }
    }
}
