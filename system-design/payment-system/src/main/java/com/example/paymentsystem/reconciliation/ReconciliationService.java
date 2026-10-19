package com.example.paymentsystem.reconciliation;

import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.outbox.OutboxService;
import com.example.paymentsystem.payment.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares a provider report with local truth. Safe automatic repair is narrow:
 * only a matching successful observation may resolve PENDING/UNKNOWN. Amount or
 * currency mismatches become reviewable breaks and never rewrite history.
 */
@Service
public class ReconciliationService {
    private final PaymentRepository payments;
    private final ReconciliationBreakRepository breaks;
    private final LedgerService ledger;
    private final OutboxService outbox;
    private final MeterRegistry metrics;

    public ReconciliationService(PaymentRepository payments, ReconciliationBreakRepository breaks,
                                 LedgerService ledger, OutboxService outbox, MeterRegistry metrics) {
        this.payments = payments;
        this.breaks = breaks;
        this.ledger = ledger;
        this.outbox = outbox;
        this.metrics = metrics;
    }

    @Transactional
    public String reconcile(ProviderObservation observation) {
        Payment payment = observation.paymentId() == null
                ? payments.findProviderPaymentForUpdate(observation.provider(), observation.providerPaymentId()).orElse(null)
                : payments.findByIdForUpdate(observation.paymentId()).orElse(null);
        if (payment == null) {
            createBreak(null, observation, "MISSING_LOCAL_PAYMENT", null, observation.status());
            return "BREAK_CREATED";
        }
        if (payment.getAmount() != observation.amount() || !payment.getCurrency().equals(observation.currency())) {
            createBreak(payment, observation, "MONEY_MISMATCH",
                    payment.getAmount() + " " + payment.getCurrency(),
                    observation.amount() + " " + observation.currency());
            return "BREAK_CREATED";
        }
        if ("SUCCEEDED".equals(observation.status())
                && (payment.getStatus() == PaymentStatus.UNKNOWN || payment.getStatus() == PaymentStatus.PENDING)) {
            long captured = payment.reconcileSucceeded(observation.providerPaymentId());
            ledger.recordMarketplaceCapture(payment.getPaymentId(), payment.getMerchantId(), captured,
                    payment.getPlatformFeeAmount(), payment.getMerchantAmount(), payment.getCurrency());
            outbox.save("Payment", payment.getPaymentId(), "PaymentReconciled", PaymentResponse.from(payment));
            metrics.counter("payment.reconciliation.repaired").increment();
            return "REPAIRED";
        }
        if (!payment.getStatus().name().equals(observation.status())) {
            createBreak(payment, observation, "STATUS_MISMATCH", payment.getStatus().name(), observation.status());
            return "BREAK_CREATED";
        }
        return "MATCHED";
    }

    /**
     * Reconciles a complete snapshot, not an arbitrary page. Only with that
     * completeness contract does absence of a provider reference mean a real
     * break instead of a pagination or time-window artifact.
     */
    @Transactional
    public SettlementReportResult reconcileCompleteSnapshot(PaymentProvider provider,
                                                              List<ProviderObservation> observations) {
        int matched = 0;
        int repaired = 0;
        int broken = 0;
        Set<String> providerReferences = observations.stream()
                .map(ProviderObservation::providerPaymentId)
                .collect(Collectors.toSet());

        for (ProviderObservation observation : observations) {
            String result = reconcile(observation);
            if ("MATCHED".equals(result)) matched++;
            else if ("REPAIRED".equals(result)) repaired++;
            else broken++;
        }

        int missing = 0;
        for (Payment payment : payments.findByProvider(provider)) {
            if (payment.getProviderPaymentId() != null
                    && financiallyFinal(payment.getStatus())
                    && !providerReferences.contains(payment.getProviderPaymentId())) {
                createBreak(payment,
                        new ProviderObservation(provider, payment.getPaymentId(), payment.getProviderPaymentId(),
                                "MISSING", payment.getAmount(), payment.getCurrency()),
                        "MISSING_PROVIDER_TRANSACTION", payment.getStatus().name(), "ABSENT_FROM_COMPLETE_SNAPSHOT");
                missing++;
                broken++;
            }
        }
        return new SettlementReportResult(observations.size(), matched, repaired, broken, missing);
    }

    private boolean financiallyFinal(PaymentStatus status) {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.CAPTURED
                || status == PaymentStatus.PARTIALLY_REFUNDED || status == PaymentStatus.REFUNDED;
    }

    private void createBreak(Payment payment, ProviderObservation observation, String type,
                             String expected, String actual) {
        breaks.save(new ReconciliationBreak(payment == null ? null : payment.getPaymentId(), observation.provider(),
                observation.providerPaymentId(), type, expected, actual));
        metrics.counter("payment.reconciliation.breaks", "type", type).increment();
    }
}
