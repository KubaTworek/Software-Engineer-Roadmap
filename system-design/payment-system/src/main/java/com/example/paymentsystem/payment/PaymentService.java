package com.example.paymentsystem.payment;

import com.example.paymentsystem.audit.AuditService;
import com.example.paymentsystem.fx.FxService;
import com.example.paymentsystem.ledger.LedgerService;
import com.example.paymentsystem.marketplace.MarketplaceSplitService;
import com.example.paymentsystem.merchant.Merchant;
import com.example.paymentsystem.merchant.MerchantRepository;
import com.example.paymentsystem.outbox.OutboxService;
import com.example.paymentsystem.psp.*;
import com.example.paymentsystem.refund.*;
import com.example.paymentsystem.risk.RiskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            AuditService auditService
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
    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, String idempotencyKey) {

        /**
         * Pobieramy merchanta, dla którego tworzona jest płatność.
         *
         * Merchant jest potrzebny nie tylko do walidacji,
         * ale też do ustalenia waluty settlementu.
         *
         * Bez istniejącego merchanta nie możemy poprawnie rozliczyć płatności.
         */
        Merchant merchant = merchantRepository.findById(request.merchantId())
                .orElseThrow(() -> new PaymentException("Merchant not found"));

        /**
         * Uruchamiamy risk assessment przed wysłaniem czegokolwiek do PSP.
         *
         * Dzięki temu podejrzane płatności można zatrzymać wcześnie,
         * bez tworzenia transakcji u zewnętrznego providera.
         */
        var risk = riskService.assess(request);

        /**
         * Jeżeli risk engine zwróci BLOCK, przerywamy flow.
         *
         * Taka płatność nie trafia do routingu PSP i nie tworzy checkoutu.
         * Chroni to system przed transakcjami, które według reguł są
         * zbyt ryzykowne.
         */
        if (risk.decision() == RiskDecision.BLOCK) {
            throw new PaymentException("Payment blocked by risk engine. score=" + risk.score());
        }

        /**
         * Wybieramy providera dla tej płatności.
         *
         * ProviderRoutingService może uwzględniać:
         * - walutę,
         * - kwotę,
         * - dostępność PSP przez circuit breaker.
         *
         * Wynikiem jest konkretny PaymentProvider, np. PAYU_MOCK.
         */
        PaymentProvider provider = routingService.route(
                request.currency(),
                request.amount()
        );

        /**
         * Tworzymy encję Payment w naszym systemie.
         *
         * Na tym etapie zapisujemy:
         * - merchanta,
         * - orderId,
         * - customerId,
         * - kwotę,
         * - walutę,
         * - capture mode,
         * - wybranego providera,
         * - risk score,
         * - risk decision.
         *
         * Payment jeszcze nie ma providerPaymentId, bo to ID dostaniemy
         * dopiero po wywołaniu PSP.
         */
        Payment payment = new Payment(
                request.merchantId(),
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                request.normalizedCaptureMode(),
                provider,
                risk.score(),
                risk.decision()
        );

        /**
         * Przeliczamy kwotę płatności na walutę settlementu merchanta.
         *
         * Przykład:
         * request amount = 10000 EUR
         * merchant settlementCurrency = PLN
         *
         * FX zapisuje:
         * - settlementAmount,
         * - settlementCurrency,
         * - fxRate.
         *
         * Dzięki temu później wiadomo, jaka kwota ma zostać rozliczona
         * wobec merchanta w jego walucie.
         */
        var fx = fxService.convert(
                request.amount(),
                request.currency(),
                merchant.getSettlementCurrency()
        );

        payment.applyFx(
                fx.targetAmount(),
                fx.targetCurrency(),
                fx.rate()
        );

        /**
         * Liczymy split marketplace.
         *
         * Split dzieli kwotę płatności na:
         * - platformFeeAmount, czyli przychód platformy,
         * - merchantAmount, czyli kwotę netto dla merchanta.
         *
         * W tej wersji split liczony jest od request.amount().
         * W systemie wielowalutowym warto świadomie zdecydować,
         * czy prowizja powinna być liczona od waluty płatności,
         * czy od waluty settlementu.
         */
        var split = splitService.split(request.amount());

        payment.applySplit(
                split.platformFeeAmount(),
                split.merchantAmount()
        );

        /**
         * Zapisujemy Payment przed wywołaniem PSP.
         *
         * Dzięki temu mamy własne paymentId, które możemy przekazać
         * do providera i powiązać z jego providerPaymentId.
         *
         * W realnym systemie trzeba bardzo ostrożnie projektować granice
         * transakcji przy wywołaniach zewnętrznych, bo request HTTP do PSP
         * nie jest częścią transakcji bazodanowej.
         */
        paymentRepository.save(payment);

        try {
            /**
             * Pobieramy klienta dla providera wybranego przez routing
             * i tworzymy płatność po stronie PSP.
             *
             * Do PSP przekazujemy:
             * - nasze paymentId,
             * - orderId,
             * - amount,
             * - currency,
             * - idempotencyKey.
             *
             * Idempotency key jest ważny także po stronie providera,
             * bo chroni przed utworzeniem kilku płatności PSP
             * przy ponowieniu requestu.
             */
            var psp = providerClientRegistry.get(provider).createPayment(new PspPaymentRequest(
                    payment.getPaymentId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    idempotencyKey
            ));

            /**
             * Podpinamy dane zwrócone przez PSP do naszej płatności.
             *
             * providerPaymentId:
             * - identyfikator płatności po stronie PSP.
             *
             * checkoutUrl:
             * - adres, na który klient może zostać przekierowany,
             *   żeby dokończyć płatność.
             */
            payment.attachProviderPayment(
                    psp.providerPaymentId(),
                    psp.checkoutUrl()
            );

            /**
             * Informujemy circuit breaker, że wywołanie providera się udało.
             *
             * To może wyzerować licznik błędów i zostawić albo przywrócić
             * providera do stanu CLOSED.
             */
            circuitBreakerService.success(provider);
        } catch (Exception e) {

            /**
             * Jeżeli komunikacja z PSP zakończy się błędem,
             * raportujemy porażkę do circuit breakera.
             *
             * Po przekroczeniu progu błędów provider zostanie oznaczony
             * jako OPEN i routing zacznie go omijać.
             */
            circuitBreakerService.failure(provider);

            /**
             * Rzucamy wyjątek dalej, żeby transakcja została wycofana
             * i klient dostał informację, że utworzenie płatności się nie udało.
             */
            throw e;
        }

        /**
         * Zapisujemy zdarzenie PaymentCreated do outboxa.
         *
         * Outbox pozwala później asynchronicznie opublikować event,
         * np. do kolejki, webhook dispatcher albo systemu raportowego.
         *
         * Event jest zapisany w tej samej transakcji co Payment,
         * więc nie zgubimy informacji o utworzonej płatności.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentCreated",
                PaymentResponse.from(payment)
        );

        return PaymentResponse.from(payment);
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

        /**
         * Księgujemy capture w ledgerze.
         *
         * To zwiększa salda:
         * - platformy z tytułu prowizji,
         * - merchanta z tytułu środków należnych do payoutu.
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
         * Zapisujemy event PaymentCaptured.
         */
        outboxService.save(
                "Payment",
                payment.getPaymentId(),
                "PaymentCaptured",
                PaymentResponse.from(payment)
        );

        return PaymentResponse.from(payment);
    }

    /**
     * Tworzy refund dla płatności.
     *
     * Refund zwraca klientowi całość albo część pobranych środków.
     *
     * Flow:
     * 1. Jeżeli podano idempotencyKey, sprawdzamy replay refundu.
     * 2. Pobieramy Payment z blokadą FOR UPDATE.
     * 3. Aktualizujemy stan refundu w encji Payment.
     * 4. Zapisujemy encję Refund.
     * 5. Księgujemy refund w ledgerze.
     * 6. Zapisujemy event PaymentRefunded do outboxa.
     *
     * @param paymentId ID płatności, którą refundujemy
     * @param request dane refundu
     * @param idempotencyKey opcjonalny klucz idempotencji refundu
     * @return utworzony albo odtworzony refund
     */
    @Transactional
    public Refund refund(
            UUID paymentId,
            RefundPaymentRequest request,
            String idempotencyKey
    ) {

        /**
         * Refund również powinien być idempotentny.
         *
         * Jeżeli klient ponowi request z tym samym idempotencyKey,
         * zwracamy istniejący Refund zamiast tworzyć drugi zwrot.
         *
         * To zabezpiecza przed podwójnym oddaniem pieniędzy klientowi.
         */
        if (idempotencyKey != null) {
            var replay = refundRepository.findByPaymentIdAndIdempotencyKey(
                    paymentId,
                    idempotencyKey
            );

            if (replay.isPresent()) {
                return replay.get();
            }
        }

        /**
         * Pobieramy płatność z blokadą.
         *
         * Refund zmniejsza dostępne saldo i zmienia stan paymentu,
         * więc musi być chroniony przed równoległymi refundami
         * oraz chargebackami.
         */
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentException("Payment not found"));

        /**
         * Metoda domenowa sprawdza, czy refund jest dozwolony
         * i ile realnie można zwrócić.
         *
         * Może to być:
         * - full refund,
         * - partial refund.
         */
        long amount = payment.refund(request.amount());

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
                amount,
                payment.getCurrency(),
                request.reason(),
                idempotencyKey
        ));

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

        return refund;
    }

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