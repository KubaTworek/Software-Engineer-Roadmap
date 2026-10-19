package com.example.notification.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** Bounded concurrency zapobiega zamianie przeciążenia providera w nieograniczoną liczbę wątków. */
@Component
public class DeliveryAdmissionController {
    private final int providerLimit;
    private final int tenantLimit;
    private final Map<String, Semaphore> providers = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> tenants = new ConcurrentHashMap<>();
    private final Counter rejected;

    @Autowired
    public DeliveryAdmissionController(@Value("${notification.admission.provider-inflight:20}") int providerLimit,
                                       @Value("${notification.admission.tenant-inflight:10}") int tenantLimit,
                                       MeterRegistry meterRegistry) {
        this.providerLimit = providerLimit; this.tenantLimit = tenantLimit;
        this.rejected = Counter.builder("notification_admission_rejected_total").register(meterRegistry);
    }
    public DeliveryAdmissionController(int providerLimit, int tenantLimit) {
        this(providerLimit, tenantLimit, new SimpleMeterRegistry());
    }

    public Permit tryAcquire(String tenantId, String provider) {
        Semaphore tenant = tenants.computeIfAbsent(tenantId, ignored -> new Semaphore(tenantLimit));
        Semaphore dependency = providers.computeIfAbsent(provider, ignored -> new Semaphore(providerLimit));
        if (!tenant.tryAcquire()) { rejected.increment(); return null; }
        if (!dependency.tryAcquire()) { tenant.release(); rejected.increment(); return null; }
        return new Permit(tenant, dependency);
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore tenant;
        private final Semaphore provider;
        private boolean released;
        private Permit(Semaphore tenant, Semaphore provider) { this.tenant = tenant; this.provider = provider; }
        @Override public void close() {
            if (!released) { released = true; provider.release(); tenant.release(); }
        }
    }
}
