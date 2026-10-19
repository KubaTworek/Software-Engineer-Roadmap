package com.example.paymentsystem.webhook;

import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.outbox.OutboxService;
import com.example.paymentsystem.payment.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class WebhookProcessor {
    private final WebhookEventRepository webhookRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledger;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final TransactionTemplate transactions;
    private final WebhookFailureRecorder failureRecorder;

    public WebhookProcessor(WebhookEventRepository webhookRepository, PaymentRepository paymentRepository,
                            LedgerService ledger, OutboxService outbox, ObjectMapper objectMapper,
                            MeterRegistry metrics, TransactionTemplate transactions,
                            WebhookFailureRecorder failureRecorder) {
        this.webhookRepository = webhookRepository;
        this.paymentRepository = paymentRepository;
        this.ledger = ledger;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.transactions = transactions;
        this.failureRecorder = failureRecorder;
    }

    public void process(UUID webhookEventId) {
        try {
            transactions.executeWithoutResult(status -> processInTransaction(webhookEventId));
        } catch (RuntimeException error) {
            failureRecorder.failed(webhookEventId, error);
            metrics.counter("payment.webhook.failures").increment();
            throw error;
        }
    }

    private void processInTransaction(UUID webhookEventId) {
        WebhookEvent webhook = webhookRepository.findById(webhookEventId).orElseThrow();
        if (webhook.getStatus() == WebhookStatus.PROCESSED) return;
        try {
            ProviderWebhookPayload payload = objectMapper.readValue(
                    webhook.getPayload(), ProviderWebhookPayload.class);
            Payment payment = payload.paymentId() != null
                    ? paymentRepository.findByIdForUpdate(payload.paymentId()).orElseThrow()
                    : paymentRepository.findProviderPaymentForUpdate(webhook.getProvider(), webhook.getProviderPaymentId())
                    .orElseThrow();

            if ("payment.succeeded".equals(webhook.getEventType())) {
                // A stale duplicate cannot move a terminal payment backwards or create a second journal.
                if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.UNKNOWN) {
                    long amount = payment.reconcileSucceeded(webhook.getProviderPaymentId());
                    ledger.recordMarketplaceCapture(payment.getPaymentId(), payment.getMerchantId(), amount,
                            payment.getPlatformFeeAmount(), payment.getMerchantAmount(), payment.getCurrency());
                    outbox.save("Payment", payment.getPaymentId(), "PaymentSucceeded", PaymentResponse.from(payment));
                }
            } else {
                throw new IllegalArgumentException("Unsupported webhook event type: " + webhook.getEventType());
            }
            webhook.processed();
            metrics.counter("payment.webhook.processed", "type", webhook.getEventType()).increment();
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Cannot process webhook", error);
        }
    }

}
