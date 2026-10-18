package com.example.notification.application;

import com.example.notification.domain.NotificationJob;
import com.example.notification.domain.ProviderSendResult;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministyczny punkt fault injection. Domyślnie jest no-op; test może zasymulować
 * śmierć procesu w najgroźniejszym oknie: provider przyjął efekt, lokalny job nadal PROCESSING.
 */
@Component
public class PostProviderEffectProbe implements Ports.PostProviderEffectHook {
    private final AtomicBoolean failNext = new AtomicBoolean();
    public void simulateCrashOnNextAcceptance() { failNext.set(true); }
    @Override public void afterAccepted(NotificationJob job, ProviderSendResult result) {
        if (failNext.compareAndSet(true, false)) throw new SimulatedProcessCrash();
    }
    public static final class SimulatedProcessCrash extends RuntimeException {
        public SimulatedProcessCrash() { super("Simulated crash after provider acceptance"); }
    }
}
