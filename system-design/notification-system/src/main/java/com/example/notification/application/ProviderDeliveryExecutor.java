package com.example.notification.application;

import com.example.notification.domain.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Jedna odpowiedzialność: wykonanie zewnętrznej próby. Klasa centralizuje admission,
 * bezpieczny fallback, historię prób i punkt awarii po efekcie. Worker zarządza lifecycle joba.
 */
@Component
public class ProviderDeliveryExecutor {
    public record Result(ProviderSendResult providerResult, boolean fallbackUsed) {}
    private final ProviderRegistry providers;
    private final DeliveryAdmissionController admission;
    private final Ports.DeliveryAttemptRepository attempts;
    private final Ports.PostProviderEffectHook effectHook;
    private final MeterRegistry metrics;
    private final Clock clock;

    public ProviderDeliveryExecutor(ProviderRegistry providers, DeliveryAdmissionController admission,
            Ports.DeliveryAttemptRepository attempts, Ports.PostProviderEffectHook effectHook,
            MeterRegistry metrics, Clock clock) {
        this.providers = providers; this.admission = admission; this.attempts = attempts;
        this.effectHook = effectHook; this.metrics = metrics; this.clock = clock;
        for (Channel channel : Channel.values()) for (Ports.NotificationProvider provider : safeProviders(channel))
            for (DeliveryAttempt.Outcome outcome : DeliveryAttempt.Outcome.values())
                Counter.builder("notification_provider_attempts_total")
                        .tag("provider", provider.providerName()).tag("outcome", outcome.name()).register(metrics);
    }

    public Result send(NotificationJob job, RenderedNotification rendered) {
        var candidates = providers.providersFor(job.getChannel());
        String recipient = job.getContactPoint().valueFor(job.getChannel());
        RuntimeException last = null;
        for (int index = 0; index < candidates.size(); index++) {
            Ports.NotificationProvider provider = candidates.get(index);
            try {
                DeliveryAdmissionController.Permit permit = admission.tryAcquire(job.getTenantId(), provider.providerName());
                if (permit == null) throw new Exceptions.AdmissionRejectedException("Delivery admission limit reached");
                ProviderSendResult result;
                try (permit) {
                    result = provider.send(job.getTenantId(), recipient, rendered, job.getId().toString());
                }
                record(job, provider.providerName(), DeliveryAttempt.Outcome.ACCEPTED, result.providerMessageId(), null);
                effectHook.afterAccepted(job, result);
                return new Result(result, index > 0);
            } catch (Exceptions.PermanentProviderException ex) {
                record(job, provider.providerName(), DeliveryAttempt.Outcome.REJECTED, null, ex.getMessage());
                throw ex;
            } catch (Exceptions.AdmissionRejectedException | PostProviderEffectProbe.SimulatedProcessCrash ex) {
                throw ex;
            } catch (Exceptions.ProviderCallException ex) {
                DeliveryAttempt.Outcome outcome = ex.certainty() == Exceptions.OutcomeCertainty.UNKNOWN
                        ? DeliveryAttempt.Outcome.UNKNOWN : DeliveryAttempt.Outcome.NOT_SENT;
                record(job, provider.providerName(), outcome, null, ex.getMessage());
                if (ex.certainty() == Exceptions.OutcomeCertainty.UNKNOWN) throw ex;
                last = ex;
            } catch (Exceptions.TransientProviderException ex) {
                record(job, provider.providerName(), DeliveryAttempt.Outcome.NOT_SENT, null, ex.getMessage());
                last = ex;
            } catch (RuntimeException ex) {
                record(job, provider.providerName(), DeliveryAttempt.Outcome.UNKNOWN, null, ex.getMessage());
                throw new Exceptions.ProviderCallException(ex.getMessage(), Exceptions.OutcomeCertainty.UNKNOWN);
            }
        }
        throw last == null ? new Exceptions.TransientProviderException("All providers failed") : last;
    }

    private void record(NotificationJob job, String provider, DeliveryAttempt.Outcome outcome,
                        String providerMessageId, String error) {
        int number = attempts.findByJobId(job.getId()).size() + 1;
        attempts.save(new DeliveryAttempt(UUID.randomUUID(), job.getId(), number, provider,
                outcome, providerMessageId, error, clock.instant()));
        Counter.builder("notification_provider_attempts_total").tag("provider", provider)
                .tag("outcome", outcome.name()).register(metrics).increment();
    }

    private java.util.List<Ports.NotificationProvider> safeProviders(Channel channel) {
        try { return providers.providersFor(channel); }
        catch (IllegalStateException missing) { return java.util.List.of(); }
    }
}
