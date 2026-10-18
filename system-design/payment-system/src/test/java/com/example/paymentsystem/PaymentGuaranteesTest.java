package com.example.paymentsystem;

import com.example.paymentsystem.idempotency.IdempotencyClaim;
import com.example.paymentsystem.idempotency.IdempotencyService;
import com.example.paymentsystem.idempotency.IdempotencyConflictException;
import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.merchant.Merchant;
import com.example.paymentsystem.merchant.MerchantRepository;
import com.example.paymentsystem.outbox.EducationalEventPublisher;
import com.example.paymentsystem.outbox.OutboxRelay;
import com.example.paymentsystem.payment.*;
import com.example.paymentsystem.payout.PayoutService;
import com.example.paymentsystem.refund.RefundRepository;
import com.example.paymentsystem.refund.RefundStatus;
import com.example.paymentsystem.reconciliation.ProviderObservation;
import com.example.paymentsystem.reconciliation.ReconciliationService;
import com.example.paymentsystem.webhook.*;
import com.example.paymentsystem.approval.*;
import com.example.paymentsystem.reconciliation.SettlementReportImporter;
import com.example.paymentsystem.reconciliation.SettlementReportResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
class PaymentGuaranteesTest {
    @Autowired MerchantRepository merchants;
    @Autowired PaymentRepository payments;
    @Autowired PaymentService paymentService;
    @Autowired LedgerService ledger;
    @Autowired IdempotencyService idempotency;
    @Autowired WebhookInboxService webhookInbox;
    @Autowired WebhookProcessor webhookProcessor;
    @Autowired WebhookSignatureVerifier signatures;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReconciliationService reconciliation;
    @Autowired OutboxRelay outboxRelay;
    @Autowired EducationalEventPublisher publisher;
    @Autowired ApprovalService approvals;
    @Autowired ApprovalRequestRepository approvalRepository;
    @Autowired SettlementReportImporter settlementReports;
    @Autowired ManualLedgerCorrectionService manualCorrections;
    @Autowired PayoutService payouts;
    @Autowired RefundRepository refunds;

    @Test
    void partialCaptureBooksOnlyTheCapturedShare() {
        Merchant merchant = merchants.save(new Merchant("Partial capture", "PLN"));
        PaymentResponse created = paymentService.create(request(merchant, "manual", CaptureMode.MANUAL), "partial-key");

        paymentService.authorize(created.paymentId());
        PaymentResponse captured = paymentService.capture(created.paymentId(), 5_000);

        assertThat(captured.capturedAmount()).isEqualTo(5_000);
        assertThat(ledger.balance("merchant:" + merchant.getMerchantId() + ":pending", "PLN")).isEqualTo(4_500);
        assertThat(ledger.balance("platform:fee_revenue", "PLN")).isGreaterThanOrEqualTo(500);
    }

    @Test
    void multiplePartialCapturesUseIndependentJournalReferencesAndCannotExceedAuthorization() {
        Merchant merchant = merchants.save(new Merchant("Incremental capture", "PLN"));
        PaymentResponse created = paymentService.create(request(merchant, "multi-capture", CaptureMode.MANUAL),
                "multi-capture-payment");
        paymentService.authorize(created.paymentId());

        paymentService.capture(created.paymentId(), 4_000, "capture-one");
        PaymentResponse completed = paymentService.capture(created.paymentId(), 6_000, "capture-two");
        PaymentResponse replay = paymentService.capture(created.paymentId(), 6_000, "capture-two");

        assertThat(completed.status()).isEqualTo(PaymentStatus.CAPTURED.name());
        assertThat(replay.capturedAmount()).isEqualTo(10_000);
        assertThat(ledger.balance("merchant:" + merchant.getMerchantId() + ":pending", "PLN"))
                .isEqualTo(9_000);
        assertThatThrownBy(() -> paymentService.capture(created.paymentId(), 1, "capture-three"))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void duplicateWebhookCreatesOneFinancialEffect() throws Exception {
        Merchant merchant = merchants.save(new Merchant("Webhook", "PLN"));
        PaymentResponse created = paymentService.create(request(merchant, "webhook", CaptureMode.AUTOMATIC), "webhook-payment");
        String raw = objectMapper.writeValueAsString(new ProviderWebhookPayload(
                "evt-" + UUID.randomUUID(), "payment.succeeded", created.paymentId(),
                created.providerPaymentId(), Instant.now()));
        long timestamp = Instant.now().getEpochSecond();
        String signature = signatures.sign(timestamp, raw);
        long clearingBefore = ledger.balance("external_psp_clearing", "PLN");

        WebhookEvent first = webhookInbox.receive(PaymentProvider.valueOf(created.provider()), timestamp, signature, raw);
        WebhookEvent duplicate = webhookInbox.receive(PaymentProvider.valueOf(created.provider()), timestamp, signature, raw);
        webhookProcessor.process(first.getWebhookEventId());
        webhookProcessor.process(duplicate.getWebhookEventId());

        assertThat(first.getWebhookEventId()).isEqualTo(duplicate.getWebhookEventId());
        assertThat(payments.findById(created.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(ledger.balance("external_psp_clearing", "PLN") - clearingBefore).isEqualTo(-10_000);
    }

    @Test
    void reconciliationRepairsOnlyMatchingProviderObservation() {
        Merchant merchant = merchants.save(new Merchant("Reconciliation", "PLN"));
        PaymentResponse created = paymentService.create(request(merchant, "reconcile", CaptureMode.AUTOMATIC), "reconcile-key");

        String result = reconciliation.reconcile(new ProviderObservation(
                PaymentProvider.valueOf(created.provider()), created.paymentId(), created.providerPaymentId(),
                "SUCCEEDED", created.amount(), created.currency()));

        assertThat(result).isEqualTo("REPAIRED");
        assertThat(payments.findById(created.paymentId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void oneOfTwoConcurrentRequestsOwnsTheIdempotencyKey() throws Exception {
        String key = "race-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { start.await(); return idempotency.claim(key, "test", "{\"a\":1}").outcome(); });
            var second = executor.submit(() -> { start.await(); return idempotency.claim(key, "test", "{\"a\":1}").outcome(); });
            start.countDown();

            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(IdempotencyClaim.Outcome.ACQUIRED, IdempotencyClaim.Outcome.IN_PROGRESS);
        }
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentMoney() {
        String key = "conflict-" + UUID.randomUUID();
        idempotency.claim(key, "create", "{\"amount\":100}");

        assertThatThrownBy(() -> idempotency.claim(key, "create", "{\"amount\":200}"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void outboxRelayPublishesDurableEvents() {
        Merchant merchant = merchants.save(new Merchant("Outbox", "PLN"));
        paymentService.create(request(merchant, "outbox", CaptureMode.AUTOMATIC), "outbox-key");
        int before = publisher.publishedEventTypes().size();

        int published = outboxRelay.publishBatch();

        assertThat(published).isPositive();
        assertThat(publisher.publishedEventTypes()).hasSizeGreaterThan(before);
    }

    @Test
    void highValueRefundRequiresTwoDifferentOperatorsAndExactApproval() {
        Merchant merchant = merchants.save(new Merchant("Maker checker", "PLN"));
        CreatePaymentRequest request = new CreatePaymentRequest(merchant.getMerchantId(), "order-approval",
                "customer", 100_000, "PLN", CaptureMode.AUTOMATIC, "PL", "PL");
        PaymentResponse created = paymentService.create(request, "approval-payment");
        paymentService.markSucceeded(created.paymentId());

        assertThatThrownBy(() -> paymentService.refund(created.paymentId(),
                new RefundPaymentRequest(100_000, "high value"), "refund-without-approval",
                "alice", null)).isInstanceOf(IllegalStateException.class);

        ApprovalRequest approval = approvals.request(ApprovalOperation.HIGH_VALUE_REFUND,
                created.paymentId().toString(), 100_000, "PLN", "alice");
        approvals.approve(approval.getApprovalId(), "bob");
        var refund = paymentService.refund(created.paymentId(),
                new RefundPaymentRequest(100_000, "high value"), "refund-approved",
                "alice", approval.getApprovalId());

        assertThat(refund.getAmount()).isEqualTo(100_000);
        assertThat(approvalRepository.findById(approval.getApprovalId()).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.CONSUMED);
    }

    @Test
    void ambiguousRefundDoesNotChangePaymentOrLedgerUntilReconciliation() {
        Merchant merchant = merchants.save(new Merchant("Unknown refund", "PLN"));
        PaymentResponse created = paymentService.create(request(merchant, "unknown-refund", CaptureMode.AUTOMATIC),
                "unknown-refund-payment");
        paymentService.markSucceeded(created.paymentId());
        long before = ledger.balance("merchant:" + merchant.getMerchantId() + ":pending", "PLN");

        assertThatThrownBy(() -> paymentService.refund(created.paymentId(),
                new RefundPaymentRequest(1_000, "ambiguous"), "simulate-timeout-refund"))
                .isInstanceOf(com.example.paymentsystem.psp.ProviderOutcomeUnknownException.class);

        var refund = refunds.findByPaymentIdAndIdempotencyKey(created.paymentId(), "simulate-timeout-refund")
                .orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.UNKNOWN);
        assertThat(payments.findById(created.paymentId()).orElseThrow().getRefundedAmount()).isZero();
        assertThat(ledger.balance("merchant:" + merchant.getMerchantId() + ":pending", "PLN")).isEqualTo(before);
    }

    @Test
    void completeSettlementSnapshotFindsExternalAndMissingProviderTransactions() {
        Merchant merchant = merchants.save(new Merchant("Settlement", "PLN"));
        PaymentResponse included = paymentService.create(request(merchant, "settlement-in", CaptureMode.AUTOMATIC),
                "settlement-in-key");
        PaymentResponse omitted = paymentService.create(request(merchant, "settlement-out", CaptureMode.AUTOMATIC),
                "settlement-out-key");
        paymentService.markSucceeded(included.paymentId());
        paymentService.markSucceeded(omitted.paymentId());

        String csv = "payment_id,provider_payment_id,status,amount,currency\n"
                + included.paymentId() + "," + included.providerPaymentId() + ",SUCCEEDED,10000,PLN\n"
                + ",provider-only-reference,SUCCEEDED,10000,PLN\n";
        var observations = settlementReports.parseCompleteSnapshot(PaymentProvider.valueOf(included.provider()), csv);
        SettlementReportResult result = reconciliation.reconcileCompleteSnapshot(
                PaymentProvider.valueOf(included.provider()), observations);

        assertThat(result.rows()).isEqualTo(2);
        assertThat(result.matched()).isGreaterThanOrEqualTo(1);
        assertThat(result.missingFromProvider()).isGreaterThanOrEqualTo(1);
        assertThat(result.breaks()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void signedButUnsupportedWebhookVersionIsRejectedBeforeInbox() throws Exception {
        String raw = objectMapper.writeValueAsString(new ProviderWebhookPayload("evt-v2", "payment.succeeded",
                UUID.randomUUID(), "provider-id", Instant.now(), 2));
        long timestamp = Instant.now().getEpochSecond();

        assertThatThrownBy(() -> webhookInbox.receive(PaymentProvider.PAYU_MOCK, timestamp,
                signatures.sign(timestamp, raw), raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported webhook version");
    }

    @Test
    void manualLedgerCorrectionRequiresExactApprovalAndKeepsJournalBalanced() {
        UUID correctionId = UUID.randomUUID();
        ApprovalRequest approval = approvals.request(ApprovalOperation.LEDGER_CORRECTION,
                correctionId.toString(), 500, "PLN", "maker");
        approvals.approve(approval.getApprovalId(), "checker");

        manualCorrections.post(new ManualLedgerCorrectionRequest(correctionId,
                "suspense:debit", "suspense:credit", 500, "PLN"), approval.getApprovalId(), "maker");

        assertThat(ledger.balance("suspense:debit", "PLN")).isEqualTo(-500);
        assertThat(ledger.balance("suspense:credit", "PLN")).isEqualTo(500);
    }

    @Test
    void highValuePayoutRequiresApprovalBoundToMerchantAndExactBalance() {
        Merchant merchant = merchants.save(new Merchant("Payout maker checker", "PLN"));
        CreatePaymentRequest request = new CreatePaymentRequest(merchant.getMerchantId(), "order-payout-approval",
                "customer", 600_000, "PLN", CaptureMode.AUTOMATIC, "PL", "PL");
        PaymentResponse created = paymentService.create(request, "payout-approval-payment");
        paymentService.markSucceeded(created.paymentId());

        // Po prowizji platformy 10% saldo merchanta wynosi 540_000 i przekracza próg zatwierdzenia.
        assertThatThrownBy(() -> payouts.create(merchant.getMerchantId(), "PLN", "payout-without-approval",
                "alice", null)).isInstanceOf(IllegalStateException.class);

        ApprovalRequest approval = approvals.request(ApprovalOperation.HIGH_VALUE_PAYOUT,
                merchant.getMerchantId().toString(), 540_000, "PLN", "alice");
        approvals.approve(approval.getApprovalId(), "bob");

        var payout = payouts.create(merchant.getMerchantId(), "PLN", "payout-approved",
                "alice", approval.getApprovalId());

        assertThat(payout.getAmount()).isEqualTo(540_000);
        assertThat(approvalRepository.findById(approval.getApprovalId()).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.CONSUMED);
        assertThat(ledger.balance("merchant:" + merchant.getMerchantId() + ":pending", "PLN")).isZero();
    }

    private CreatePaymentRequest request(Merchant merchant, String suffix, CaptureMode mode) {
        return new CreatePaymentRequest(merchant.getMerchantId(), "order-" + suffix, "customer", 10_000,
                "PLN", mode, "PL", "PL");
    }
}
