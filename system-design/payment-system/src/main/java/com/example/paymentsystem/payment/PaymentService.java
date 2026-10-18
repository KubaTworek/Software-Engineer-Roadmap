package com.example.paymentsystem.payment;

import com.example.paymentsystem.audit.AuditService;
import com.example.paymentsystem.approval.ApprovalOperation;
import com.example.paymentsystem.approval.ApprovalService;
import com.example.paymentsystem.fx.FxService;
import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.marketplace.MarketplaceSplitService;
import com.example.paymentsystem.merchant.Merchant;
import com.example.paymentsystem.merchant.MerchantRepository;
import com.example.paymentsystem.outbox.OutboxService;
import com.example.paymentsystem.psp.*;
import com.example.paymentsystem.refund.*;
import com.example.paymentsystem.risk.RiskService;
import com.example.paymentsystem.operation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.UUID;

/**
 * Główny serwis domenowy płatności.
 *
 * PaymentService koordynuje najważniejsze flow całego Payment Systemu.
 * To tutaj łączą się komponenty odpowiedzialne za:
 * - merchantów,
 * - risk scoring,
 * - routing PSP,
 * - komunikację z providerem,
 * - FX,
 * - marketplace split,
 * - ledger,
 * - refundy,
 * - outbox,
 * - audit.
 *
 * Ta klasa nie powinna zawierać szczegółów technicznych konkretnych PSP.
 * Od tego są PaymentProviderClient i ProviderClientRegistry.
 *
 * PaymentService odpowiada za proces biznesowy:
 * - czy płatność może zostać utworzona,
 * - do którego providera trafi,
 * - jaki będzie jej status,
 * - jakie zdarzenia zostaną opublikowane,
 * - jakie księgowania powstaną w ledgerze.
 */
@Service
public class PaymentService {

    /**
     * Repozytorium płatności.
     *
     * Służy do zapisu i odczytu głównej encji Payment.
     */
    private final PaymentRepository paymentRepository;

    /**
     * Repozytorium merchantów.
     *
     * Potrzebne przy tworzeniu płatności, żeby:
     * - sprawdzić, czy merchant istnieje,
     * - pobrać jego settlementCurrency,
     * - poprawnie wykonać FX settlementu.
     */
    private final MerchantRepository merchantRepository;

    /**
     * Serwis routingu PSP.
     *
     * Decyduje, który Payment Service Provider powinien obsłużyć płatność.
     *
     * Routing może uwzględniać walutę, kwotę i dostępność providerów
     * przez circuit breaker.
     */
    private final ProviderRoutingService routingService;

    /**
     * Rejestr klientów PSP.
     *
     * Po tym, jak routing wybierze providera, registry zwraca właściwego
     * klienta integracyjnego.
     *
     * Dzięki temu PaymentService nie tworzy ręcznie klientów PSP
     * i nie zna klas typu MockProviderClient.
     */
    private final ProviderClientRegistry providerClientRegistry;

    /**
     * Circuit breaker dla PSP.
     *
     * PaymentService raportuje do niego sukcesy i błędy komunikacji
     * z providerami.
     *
     * Dzięki temu routing może omijać PSP, które zaczynają seryjnie zawodzić.
     */
    private final CircuitBreakerService circuitBreakerService;

    /**
     * Serwis oceny ryzyka.
     *
     * RiskService wylicza score i decyzję:
     * - ALLOW,
     * - REVIEW,
     * - BLOCK.
     *
     * Decyzja risk engine wpływa na to, czy płatność zostanie utworzona.
     */
    private final RiskService riskService;

    /**
     * Serwis FX.
     *
     * Przelicza kwotę płatności z waluty klienta na walutę settlementu merchanta.
     *
     * Przykład:
     * klient płaci w EUR,
     * merchant rozlicza się w PLN,
     * system zapisuje settlementAmount w PLN.
     */
    private final FxService fxService;

    /**
     * Serwis marketplace split.
     *
     * Dzieli kwotę płatności na:
     * - prowizję platformy,
     * - kwotę netto należną merchantowi.
     */
    private final MarketplaceSplitService splitService;

    /**
     * LedgerService zapisuje finansowe skutki płatności.
     *
     * To ledger jest księgowym źródłem prawdy.
     *
     * PaymentService wywołuje go przy:
     * - sukcesie płatności,
     * - capture,
     * - refundzie.
     */
    private final LedgerService ledgerService;

    /**
     * Repozytorium refundów.
     *
     * Służy do zapisu refundów i obsługi idempotencji refundu
     * na poziomie paymentId + idempotencyKey.
     */
    private final RefundRepository refundRepository;

    /**
     * OutboxService zapisuje zdarzenia domenowe w tej samej transakcji
     * co zmiana encji Payment.
     *
     * Dzięki temu inne części systemu mogą później bezpiecznie opublikować
     * eventy do brokera wiadomości bez ryzyka utraty informacji.
     */
    private final OutboxService outboxService;

    /**
     * AuditService zapisuje operacje administracyjne lub techniczne,
     * które powinny zostać w śladzie audytowym.
     *
     * W tej klasie używamy go przy ręcznym zarejestrowaniu awarii providera.
     */
    private final AuditService auditService;
    private final PaymentAttemptRepository attemptRepository;
    private final TransactionTemplate transactions;
    private final MeterRegistry metrics;
    private final ApprovalService approvals;
    private final CaptureRepository captureRepository;
    private final ProviderOperationRepository providerOperations;

    public PaymentService(
            PaymentRepository paymentRepository,
            MerchantRepository merchantRepository,
            ProviderRoutingService routingService,
            ProviderClientRegistry providerClientRegistry,
            CircuitBreakerService circuitBreakerService,
            RiskService riskService,
            FxService fxService,
            MarketplaceSplitService splitService,
            LedgerService ledgerService,
            RefundRepository refundRepository,
            OutboxService outboxService,
            AuditService auditService,
            PaymentAttemptRepository attemptRepository,
            TransactionTemplate transactions,
            MeterRegistry metrics,
            ApprovalService approvals,
            CaptureRepository captureRepository,
            ProviderOperationRepository providerOperations
    ) {
        this.paymentRepository = paymentRepository;
        this.merchantRepository = merchantRepository;
        this.routingService = routingService;
        this.providerClientRegistry = providerClientRegistry;
        this.circuitBreakerService = circuitBreakerService;
        this.riskService = riskService;
        this.fxService = fxService;
        this.splitService = splitService;
        this.ledgerService = ledgerService;
        this.refundRepository = refundRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.attemptRepository = attemptRepository;
        this.transactions = transactions;
        this.metrics = metrics;
        this.approvals = approvals;
        this.captureRepository = captureRepository;
        this.providerOperations = providerOperations;
    }

    /**
     * Tworzy nową płatność.
     *
     * To jest początek głównego flow płatniczego.
     *
     * Flow:
     * 1. Pobieramy merchanta.
     * 2. Oceniamy ryzyko płatności.
     * 3. Blokujemy płatność, jeżeli risk engine zwróci BLOCK.
     * 4. Wybieramy PSP przez routing.
     * 5. Tworzymy encję Payment.
     * 6. Przeliczamy kwotę settlementu przez FX.
     * 7. Liczymy split marketplace.
     * 8. Zapisujemy Payment.
     * 9. Tworzymy płatność u providera.
     * 10. Zapisujemy providerPaymentId i checkoutUrl.
     * 11. Raportujemy sukces albo błąd do circuit breakera.
     * 12. Zapisujemy event PaymentCreated do outboxa.
     *
     * @param request dane potrzebne do utworzenia płatności
     * @param idempotencyKey idempotency key przekazany do PSP
     * @return odpowiedź z aktualnym stanem płatności
     */
    public PaymentResponse create(CreatePaymentRequest request, String idempotencyKey) {
        /*
         * Transakcja lokalna kończy się PRZED requestem HTTP. Dzięki temu nie
         * trzymamy połączenia i blokad DB podczas nieprzewidywalnego I/O, a po
         * timeoutcie pozostaje trwały PaymentAttempt do reconciliation.
         */
        PreparedPayment prepared = transactions.execute(status -> preparePayment(request, idempotencyKey));
        if (prepared == null) throw new IllegalStateException("Could not prepare payment");

        try {
            var psp = providerClientRegistry.get(prepared.provider()).createPayment(new PspPaymentRequest(
                    prepared.paymentId(), request.orderId(), request.amount(), request.currency(), idempotencyKey));
            circuitBreakerService.success(prepared.provider());

            PaymentResponse response = transactions.execute(status -> {
                Payment payment = paymentRepository.findByIdForUpdate(prepared.paymentId())
                        .orElseThrow(() -> new PaymentException("Prepared payment not found"));
                PaymentAttempt attempt = attemptRepository.findById(prepared.attemptId())
                        .orElseThrow(() -> new PaymentException("Payment attempt not found"));
                payment.attachProviderPayment(psp.providerPaymentId(), psp.checkoutUrl());
                attempt.succeed(psp.providerPaymentId());
                outboxService.save("Payment", payment.getPaymentId(), "PaymentCreated", PaymentResponse.from(payment));
                return PaymentResponse.from(payment);
            });
            metrics.counter("payment.provider.calls", "provider", prepared.provider().name(), "outcome", "success")
                    .increment();
            return response;
        } catch (ProviderCallException error) {
            if (error.safeToRetry()) {
                circuitBreakerService.failure(prepared.provider());
                transactions.executeWithoutResult(status -> {
                    Payment payment = paymentRepository.findByIdForUpdate(prepared.paymentId())
                            .orElseThrow(() -> new PaymentException("Prepared payment not found"));
                    PaymentAttempt attempt = attemptRepository.findById(prepared.attemptId())
                            .orElseThrow(() -> new PaymentException("Payment attempt not found"));
                    payment.markProviderAttemptNotSent();
                    attempt.failedBeforeDispatch(error);
                });
                metrics.counter("payment.provider.calls", "provider", prepared.provider().name(),
                        "outcome", "not_sent").increment();
                throw error;
            }
            markProviderOutcomeUnknown(prepared, error);
            throw new ProviderOutcomeUnknownException(prepared.paymentId());
        } catch (RuntimeException error) {
            markProviderOutcomeUnknown(prepared, error);
            throw new ProviderOutcomeUnknownException(prepared.paymentId());
        }
    }

    private void markProviderOutcomeUnknown(PreparedPayment prepared, RuntimeException error) {
            circuitBreakerService.failure(prepared.provider());
            transactions.executeWithoutResult(status -> {
                Payment payment = paymentRepository.findByIdForUpdate(prepared.paymentId())
                        .orElseThrow(() -> new PaymentException("Prepared payment not found"));
                PaymentAttempt attempt = attemptRepository.findById(prepared.attemptId())
                        .orElseThrow(() -> new PaymentException("Payment attempt not found"));
                payment.markProviderOutcomeUnknown();
                attempt.unknown(error);
                outboxService.save("Payment", payment.getPaymentId(), "PaymentProviderOutcomeUnknown",
                        PaymentResponse.from(payment));
            });
            metrics.counter("payment.provider.calls", "provider", prepared.provider().name(), "outcome", "unknown")
                    .increment();
    }

    private PreparedPayment preparePayment(CreatePaymentRequest request, String idempotencyKey) {
        Merchant merchant = merchantRepository.findById(request.merchantId())
                .orElseThrow(() -> new PaymentException("Merchant not found"));
        var risk = riskService.assess(request);
        if (risk.decision() == RiskDecision.BLOCK) {
            throw new PaymentException("Payment blocked by risk engine. score=" + risk.score());
        }
        PaymentProvider provider = routingService.route(request.currency(), request.amount());
        if (request.normalizedCaptureMode() == CaptureMode.MANUAL
                && !request.normalizedPaymentMethod().supportsManualCapture()) {
            throw new PaymentException("Manual capture is not supported by " + request.normalizedPaymentMethod());
        }
        Payment payment = new Payment(request.merchantId(), request.orderId(), request.customerId(), request.amount(),
                request.currency(), request.normalizedCaptureMode(), request.normalizedPaymentMethod(), provider,
                risk.score(), risk.decision());
        var fx = fxService.convert(request.amount(), request.currency(), merchant.getSettlementCurrency());
        payment.applyFx(fx.targetAmount(), fx.targetCurrency(), fx.rate());
        var split = splitService.split(request.amount());
        payment.applySplit(split.platformFeeAmount(), split.merchantAmount());
        payment.startProviderAttempt();
        payment = paymentRepository.save(payment);
        PaymentAttempt attempt = new PaymentAttempt(payment.getPaymentId(), provider, idempotencyKey);
        attempt.start();
        attempt = attemptRepository.save(attempt);
        return new PreparedPayment(payment.getPaymentId(), attempt.getAttemptId(), provider);
    }

    private record PreparedPayment(UUID paymentId, UUID attemptId, PaymentProvider provider) {
    }

    /**
     * Pobiera płatność po ID.
     *
     * To jest operacja tylko do odczytu.
     *
     * @param paymentId ID płatności
     * @return aktualny stan płatności
     */
    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId) {
        return PaymentResponse.from(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found")));
    }

    /**
     * Oznacza płatność jako zakończoną sukcesem.
     *
     * W tym projekcie metoda symuluje webhook od PSP.
     * W realnym systemie status SUCCEEDED byłby zwykle ustawiany
     * po otrzymaniu podpisanego webhooka od providera.
     *
     * Flow:
     * 1. Pobieramy Payment z blokadą FOR UPDATE.
     * 2. Zmieniamy status przez payment.succeed().
     * 3. Księgujemy marketplace capture w ledgerze.
     * 4. Zapisujemy event PaymentSucceeded do outboxa.
     * 5. Zwracamy aktualny stan płatności.
     *
     * @param paymentId ID płatności
     * @return płatność po oznaczeniu jako SUCCEEDED
     */
    @Transactional
    public PaymentResponse markSucceeded(UUID paymentId) {

        /**
         * Pobieramy Payment z blokadą pesymistyczną.
         *
         * To chroni przed równoległymi zmianami tej samej płatności,
         * np. jednoczesnym succeed i refund/capture.
         */
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        /**
         * Zmieniamy status płatności na SUCCEEDED.
         *
         * Metoda domenowa payment.succeed() zwraca captured amount,
         * czyli kwotę, którą należy zaksięgować.
         */
        long captured = payment.succeed();

        /**
         * Księgujemy finansowy skutek udanej płatności.
         *
         * Ledger zapisuje double-entry:
         * - środki klienta / PSP,
         * - prowizję platformy,
         * - kwotę należną merchantowi.
         *
         * To jest moment, w którym payment zaczyna mieć realny wpływ
         * na salda finansowe systemu.
         */
        ledgerService.recordMarketplaceCapture(
                payment.getPaymentId(),
                payment.getMerchantId(),
                captured,
                payment.getPlatformFeeAmount(),
                payment.getMerchantAmount(),
                payment.getCurrency()
        );

        /**
         * Zapisujemy event do outboxa.
         *
         * Inne procesy mogą na tej podstawie wysłać webhook,
         * powiadomić merchanta albo zaktualizować read model.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentSucceeded",
                PaymentResponse.from(payment)
        );
        auditService.record("system", "PAYMENT_SUCCEEDED", "PAYMENT", payment.getPaymentId().toString(),
                "captured=" + captured + ",currency=" + payment.getCurrency());

        return PaymentResponse.from(payment);
    }

    /**
     * Autoryzuje płatność.
     *
     * Authorization blokuje środki klienta,
     * ale jeszcze ich nie pobiera.
     *
     * Jest to potrzebne przy manual capture flow.
     *
     * Flow:
     * 1. Pobieramy Payment z blokadą FOR UPDATE.
     * 2. Zmieniamy status na AUTHORIZED.
     * 3. Zapisujemy event PaymentAuthorized do outboxa.
     *
     * @param paymentId ID płatności
     * @return płatność po autoryzacji
     */
    @Transactional
    public PaymentResponse authorize(UUID paymentId) {

        /**
         * Lock FOR UPDATE chroni przed równoległą autoryzacją,
         * anulowaniem albo capture tej samej płatności.
         */
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        /**
         * Metoda domenowa sprawdza, czy aktualny status pozwala
         * przejść do AUTHORIZED.
         *
         * Reguły statusów powinny być trzymane w encji Payment,
         * a nie rozproszone po controllerach.
         */
        payment.authorize();

        /**
         * Zapisujemy event autoryzacji.
         *
         * Autoryzacja może być ważna dla:
         * - panelu merchanta,
         * - webhooków,
         * - procesów rezerwacji towaru,
         * - późniejszego capture.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentAuthorized",
                PaymentResponse.from(payment)
        );
        auditService.record("operator", "PAYMENT_AUTHORIZED", "PAYMENT", payment.getPaymentId().toString(), null);

        return PaymentResponse.from(payment);
    }

    /**
     * Wykonuje capture wcześniej autoryzowanej płatności.
     *
     * Capture oznacza finalne pobranie środków.
     *
     * Flow:
     * 1. Pobieramy Payment z blokadą FOR UPDATE.
     * 2. Wykonujemy capture w encji Payment.
     * 3. Księgujemy marketplace capture w ledgerze.
     * 4. Zapisujemy event PaymentCaptured do outboxa.
     *
     * @param paymentId ID płatności
     * @param amount kwota do pobrania w najmniejszej jednostce waluty
     * @return płatność po capture
     */
    @Transactional
    public PaymentResponse capture(UUID paymentId, long amount) {
        return capture(paymentId, amount, "capture-" + UUID.randomUUID());
    }

    /**
     * Wariant idempotentny wymagany przez publiczne API. Każdy partial capture
     * ma własną tożsamość i własny journal ledgerowy, dlatego kolejne pobranie
     * nie nadpisuje ani nie deduplikuje poprzedniego.
     */
    @Transactional
    public PaymentResponse capture(UUID paymentId, long amount, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for capture");
        }
        if (captureRepository.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey).isPresent()) {
            return get(paymentId);
        }

        /**
         * Pobieramy płatność z blokadą.
         *
         * Capture zmienia stan finansowy, więc nie może ścigać się
         * z refundem, cancel albo drugim capture.
         */
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        /**
         * Metoda domenowa wykonuje walidację statusu i kwoty.
         *
         * Zwraca faktycznie pobraną kwotę, którą trzeba zaksięgować.
         */
        long captured = payment.capture(amount);
        Capture capture = captureRepository.save(new Capture(paymentId, captured, payment.getCurrency(), idempotencyKey));

        /* Partial capture earns only the proportional fee. Reusing the split
         * calculated for the full authorization would make the journal
         * unbalanced and overstate both revenue and merchant liability. */
        var capturedSplit = splitService.split(captured);

        /**
         * Księgujemy capture w ledgerze.
         *
         * To zwiększa salda:
         * - platformy z tytułu prowizji,
         * - merchanta z tytułu środków należnych do payoutu.
         */
        ledgerService.recordMarketplaceCapture(
                capture.getCaptureId(),
                payment.getMerchantId(),
                captured,
                capturedSplit.platformFeeAmount(),
                capturedSplit.merchantAmount(),
                payment.getCurrency()
        );

        /**
         * Zapisujemy event PaymentCaptured.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentCaptured",
                PaymentResponse.from(payment)
        );
        auditService.record("operator", "PAYMENT_CAPTURED", "PAYMENT", payment.getPaymentId().toString(),
                "captureId=" + capture.getCaptureId() + ",captured=" + captured
                        + ",currency=" + payment.getCurrency());

        return PaymentResponse.from(payment);
    }

    /**
     * Tworzy refund dla płatności.
     *
     * Refund zwraca klientowi całość albo część pobranych środków.
     *
     * Flow:
     * 1. Jeżeli podano idempotencyKey, sprawdzamy replay refundu.
     * 2. W krótkiej transakcji zapisujemy Refund PROCESSING i ProviderOperation.
     * 3. Poza transakcją wywołujemy PSP z tym samym idempotency key.
     * 4. Dopiero potwierdzony sukces zmienia Payment i księguje ledger.
     * 5. Timeout po wysłaniu pozostawia Refund UNKNOWN bez lokalnego ruchu pieniędzy.
     *
     * @param paymentId ID płatności, którą refundujemy
     * @param request dane refundu
     * @param idempotencyKey wymagany klucz idempotencji refundu
     * @return utworzony albo odtworzony refund
     */
    public Refund refund(
            UUID paymentId,
            RefundPaymentRequest request,
            String idempotencyKey
    ) {
        return refund(paymentId, request, idempotencyKey, "system", null);
    }

    public Refund refund(UUID paymentId, RefundPaymentRequest request, String idempotencyKey,
                         String actor, UUID approvalId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required for refund");
        }

        /**
         * Refund również powinien być idempotentny.
         *
         * Jeżeli klient ponowi request z tym samym idempotencyKey,
         * zwracamy istniejący Refund zamiast tworzyć drugi zwrot.
         *
         * To zabezpiecza przed podwójnym oddaniem pieniędzy klientowi.
         */
        var replay = refundRepository.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey);
        if (replay.isPresent()) return replay.get();

        PreparedRefund prepared = transactions.execute(status -> prepareRefund(
                paymentId, request, idempotencyKey, actor, approvalId));
        if (prepared == null) throw new IllegalStateException("Could not prepare refund");

        try {
            PspOperationResponse response = providerClientRegistry.get(prepared.provider()).execute(
                    new PspOperationRequest(prepared.operationId(), paymentId, prepared.providerPaymentId(),
                            ProviderOperationType.REFUND, prepared.amount(), prepared.currency(), idempotencyKey));
            return transactions.execute(status -> finalizeRefund(prepared, response.providerReference(), actor));
        } catch (ProviderCallException error) {
            transactions.executeWithoutResult(status -> {
                Refund refund = refundRepository.findById(prepared.refundId()).orElseThrow();
                ProviderOperation operation = providerOperations.findById(prepared.operationId()).orElseThrow();
                if (error.safeToRetry()) {
                    refund.failed();
                    operation.failBeforeDispatch(error);
                } else {
                    refund.unknown();
                    operation.markUnknown(error);
                }
            });
            if (error.safeToRetry()) throw error;
            throw new ProviderOutcomeUnknownException(paymentId);
        }
    }

    private PreparedRefund prepareRefund(UUID paymentId, RefundPaymentRequest request, String idempotencyKey,
                                         String actor, UUID approvalId) {

        /**
         * Pobieramy płatność z blokadą.
         *
         * Refund zmniejsza dostępne saldo i zmienia stan paymentu,
         * więc musi być chroniony przed równoległymi refundami
         * oraz chargebackami.
         */
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        if (!payment.getPaymentMethod().supportsRefunds()) {
            throw new PaymentException("Payment method does not support refunds: " + payment.getPaymentMethod());
        }
        if (approvals.refundNeedsApproval(request.amount())) {
            approvals.requireAndConsume(ApprovalOperation.HIGH_VALUE_REFUND, paymentId.toString(),
                    request.amount(), payment.getCurrency(), approvalId);
        }

        /**
         * Metoda domenowa sprawdza, czy refund jest dozwolony
         * i ile realnie można zwrócić.
         *
         * Może to być:
         * - full refund,
         * - partial refund.
         */
        long refundable = payment.getCapturedAmount() - payment.getRefundedAmount();
        if (request.amount() <= 0 || request.amount() > refundable) {
            throw new PaymentException("Invalid refund amount");
        }

        /**
         * Zapisujemy encję Refund.
         *
         * Refund jest osobnym rekordem, bo jedna płatność może mieć
         * wiele częściowych refundów.
         *
         * Przechowujemy też reason oraz idempotencyKey,
         * żeby móc audytować i bezpiecznie odtwarzać request.
         */
        Refund refund = refundRepository.save(new Refund(
                paymentId,
                request.amount(),
                payment.getCurrency(),
                request.reason(),
                idempotencyKey
        ));
        ProviderOperation operation = new ProviderOperation(refund.getRefundId(), ProviderOperationType.REFUND,
                payment.getProvider(), idempotencyKey);
        operation.start();
        operation = providerOperations.save(operation);
        auditService.record(actor, "PAYMENT_REFUND_REQUESTED", "PAYMENT", paymentId.toString(),
                "refundId=" + refund.getRefundId() + ",amount=" + request.amount());
        return new PreparedRefund(refund.getRefundId(), operation.getOperationId(), paymentId,
                payment.getProvider(), payment.getProviderPaymentId(), request.amount(), payment.getCurrency());
    }

    private Refund finalizeRefund(PreparedRefund prepared, String providerReference, String actor) {
        Payment payment = paymentRepository.findByIdForUpdate(prepared.paymentId()).orElseThrow();
        Refund refund = refundRepository.findById(prepared.refundId()).orElseThrow();
        ProviderOperation operation = providerOperations.findById(prepared.operationId()).orElseThrow();
        long amount = payment.refund(prepared.amount());
        var refundSplit = splitService.split(amount);
        refund.succeed(providerReference);
        operation.succeed(providerReference);

        /**
         * Księgujemy refund w ledgerze.
         *
         * Refund powinien odwrócić odpowiednią część wcześniejszego capture:
         * - zmniejszyć środki należne merchantowi,
         * - zmniejszyć przychód/prowizję platformy, jeżeli taki model przyjmujemy,
         * - pokazać wypływ środków do klienta.
         */
        ledgerService.recordRefund(
                refund.getRefundId(),
                payment.getMerchantId(),
                amount,
                refundSplit.platformFeeAmount(),
                refundSplit.merchantAmount(),
                payment.getCurrency()
        );

        /**
         * Zapisujemy event PaymentRefunded.
         *
         * Taki event może uruchomić webhook do merchanta,
         * mail do klienta albo aktualizację raportów.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentRefunded",
                PaymentResponse.from(payment)
        );
        auditService.record(actor, "PAYMENT_REFUNDED", "PAYMENT", payment.getPaymentId().toString(),
                "refundId=" + refund.getRefundId() + ",amount=" + amount);

        return refund;
    }

    private record PreparedRefund(UUID refundId, UUID operationId, UUID paymentId, PaymentProvider provider,
                                  String providerPaymentId, long amount, String currency) { }

    /**
     * Anuluje płatność.
     *
     * Cancel ma sens przed finalnym pobraniem środków.
     * Najczęściej dotyczy płatności autoryzowanej,
     * która nie została jeszcze przechwycona przez capture.
     *
     * Flow:
     * 1. Pobieramy Payment z blokadą FOR UPDATE.
     * 2. Zmieniamy status przez payment.cancel().
     * 3. Zapisujemy event PaymentCanceled do outboxa.
     *
     * @param paymentId ID płatności
     * @return płatność po anulowaniu
     */
    @Transactional
    public PaymentResponse cancel(UUID paymentId) {

        /**
         * Blokada chroni przed konfliktem między cancel a capture.
         *
         * Bez locka mogłoby dojść do sytuacji, w której jedna transakcja
         * anuluje płatność, a druga w tym samym czasie ją capture'uje.
         */
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        /**
         * Encja Payment pilnuje, czy anulowanie jest dozwolone
         * dla aktualnego statusu płatności.
         */
        payment.cancel();

        /**
         * Zapisujemy event anulowania.
         *
         * Cancel nie księguje niczego w ledgerze,
         * bo nie doszło do finalnego pobrania środków.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentCanceled",
                PaymentResponse.from(payment)
        );
        auditService.record("operator", "PAYMENT_CANCELED", "PAYMENT", payment.getPaymentId().toString(), null);

        return PaymentResponse.from(payment);
    }

    /**
     * Ręcznie rejestruje błąd providera.
     *
     * To jest operacja administracyjna.
     *
     * Może być użyta, gdy operator systemu wie, że dany PSP ma problem
     * i chce przyspieszyć otwarcie circuit breakera.
     *
     * Flow:
     * 1. Rejestrujemy failure w CircuitBreakerService.
     * 2. Zapisujemy operację w audit logu.
     *
     * @param provider PSP, dla którego rejestrujemy błąd
     * @param actor użytkownik lub system wykonujący operację
     */
    @Transactional
    public void recordProviderFailure(
            PaymentProvider provider,
            String actor
    ) {

        /**
         * Zwiększamy licznik błędów providera.
         *
         * Po przekroczeniu progu circuit breaker przejdzie w stan OPEN
         * i routing przestanie wybierać tego PSP.
         */
        circuitBreakerService.failure(provider);

        /**
         * Zapisujemy audyt operacji.
         *
         * Ręczna ingerencja w stan PSP powinna być widoczna,
         * bo wpływa bezpośrednio na routing płatności.
         */
        auditService.record(
                actor,
                "RECORD_PROVIDER_FAILURE",
                "PROVIDER",
                provider.name(),
                null
        );
    }
}
