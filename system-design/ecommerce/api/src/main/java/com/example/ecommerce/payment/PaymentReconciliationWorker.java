package com.example.ecommerce.payment;

import com.example.ecommerce.monitoring.BusinessMetrics;
import com.example.ecommerce.payment.dto.PaymentDtos;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Naprawia „unknown outcome” po timeoutach. Worker nie zgaduje wyniku: aktualizuje
 * lokalny stan wyłącznie, gdy PSP zwróci terminalny, audytowalny rezultat.
 */
@Component
@ConditionalOnProperty(prefix = "app.payments.reconciliation", name = "enabled", havingValue = "true")
public class PaymentReconciliationWorker {
    private final PaymentRepository payments;
    private final PaymentProviderClient provider;
    private final PaymentService paymentService;
    private final Clock clock;
    private final BusinessMetrics metrics;

    public PaymentReconciliationWorker(
            PaymentRepository payments,
            PaymentProviderClient provider,
            PaymentService paymentService,
            Clock clock,
            BusinessMetrics metrics
    ) {
        this.payments = payments;
        this.provider = provider;
        this.paymentService = paymentService;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.payments.reconciliation.fixed-delay-ms:60000}")
    public void reconcileUnknownPayments() {
        var threshold = clock.instant().minus(Duration.ofMinutes(2));
        for (Payment payment : payments.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                PaymentStatus.PENDING, threshold)) {
            var result = provider.lookup(payment);
            if (result.isEmpty()) {
                metrics.paymentReconciliationStillUnknown();
                continue;
            }

            var terminalResult = result.orElseThrow();
            paymentService.handleProviderEvent(new PaymentDtos.ProviderWebhookEvent(
                    terminalResult.eventId(), payment.getId(), terminalResult.providerPaymentId(),
                    terminalResult.status(), payment.getAmount(), payment.getCurrency()));
            metrics.paymentReconciliationResolved();
        }
    }
}
