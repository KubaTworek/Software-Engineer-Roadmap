package com.example.paymentsystem.payment;

import com.example.paymentsystem.idempotency.IdempotencyRecord;
import com.example.paymentsystem.idempotency.IdempotencyService;
import com.example.paymentsystem.refund.Refund;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller odpowiedzialny za główne operacje na płatnościach.
 *
 * PaymentController wystawia publiczne API systemu płatniczego.
 * To przez te endpointy klient systemu może:
 * - utworzyć płatność,
 * - pobrać szczegóły płatności,
 * - oznaczyć płatność jako udaną,
 * - wykonać autoryzację,
 * - wykonać capture,
 * - anulować płatność,
 * - wykonać refund.
 *
 * Controller nie zawiera logiki biznesowej płatności.
 * Jego główne zadania to:
 * - odebranie requestu HTTP,
 * - walidacja danych wejściowych,
 * - obsługa idempotencji dla create payment,
 * - delegowanie operacji do PaymentService,
 * - zwrócenie odpowiedzi HTTP.
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    /**
     * Główny serwis domenowy płatności.
     *
     * To PaymentService odpowiada za:
     * - tworzenie paymentu,
     * - risk assessment,
     * - routing PSP,
     * - komunikację z providerem,
     * - zmianę statusów,
     * - capture,
     * - refund,
     * - księgowania w ledgerze.
     */
    private final PaymentService paymentService;

    /**
     * Serwis idempotencji.
     *
     * Chroni endpoint tworzenia płatności przed podwójnym wykonaniem
     * tej samej operacji.
     *
     * Jest to krytyczne w płatnościach, bo klient HTTP może ponowić request
     * po timeoutcie, zerwanym połączeniu albo błędzie sieci.
     */
    private final IdempotencyService idempotencyService;

    /**
     * ObjectMapper służy tutaj do zbudowania kanonicznej reprezentacji requestu
     * oraz zapisania odpowiedzi w rekordzie idempotencji.
     *
     * Dzięki temu replay może zwrócić dokładnie tę samą odpowiedź,
     * którą klient dostał przy pierwszym poprawnym requestcie.
     */
    private final ObjectMapper objectMapper;

    public PaymentController(
            PaymentService paymentService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Tworzy nową płatność.
     *
     * Ten endpoint wymaga nagłówka Idempotency-Key.
     *
     * Dlaczego?
     * Tworzenie płatności jest operacją, której nie wolno przypadkowo
     * wykonać dwa razy.
     *
     * Przykład problemu bez idempotencji:
     * 1. Klient wysyła POST /v1/payments.
     * 2. System tworzy płatność u PSP.
     * 3. Połączenie HTTP zrywa się przed zwróceniem odpowiedzi.
     * 4. Klient ponawia request.
     * 5. Bez idempotencji mogłaby powstać druga płatność.
     *
     * Flow:
     * 1. Serializujemy request do kanonicznej postaci JSON.
     * 2. Sprawdzamy, czy Idempotency-Key był już użyty dla tego endpointu.
     * 3. Jeżeli tak, zwracamy zapisaną odpowiedź.
     * 4. Jeżeli nie, tworzymy płatność w PaymentService.
     * 5. Zapisujemy odpowiedź w tabeli idempotencji.
     * 6. Zwracamy HTTP 201 CREATED.
     *
     * @param key idempotency key przekazany przez klienta
     * @param request dane potrzebne do utworzenia płatności
     * @return zapisane lub nowo utworzone body odpowiedzi HTTP
     */
    @PostMapping
    public ResponseEntity<String> create(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreatePaymentRequest request
    ) throws Exception {

        /**
         * Budujemy kanoniczną reprezentację requestu.
         *
         * IdempotencyService porównuje hash tego requestu z hashem
         * zapisanym przy pierwszym użyciu danego Idempotency-Key.
         *
         * Dzięki temu ten sam key nie może zostać użyty do innej płatności.
         */
        String canonical = objectMapper.writeValueAsString(request);

        /**
         * Sprawdzamy, czy istnieje wcześniejsza odpowiedź dla:
         * - tego idempotency key,
         * - tego endpointu,
         * - tego samego body requestu.
         *
         * Jeżeli request jest identyczny, możemy bezpiecznie zwrócić replay.
         * Jeżeli key był użyty z innym body, IdempotencyService powinien
         * zgłosić konflikt.
         */
        var replay = idempotencyService.findReplay(
                key,
                "POST:/v1/payments",
                canonical
        );

        /**
         * Jeżeli znaleźliśmy replay, nie wykonujemy ponownie PaymentService.
         *
         * To jest najważniejszy fragment idempotencji:
         * ponowiony request nie tworzy nowej płatności,
         * tylko dostaje dokładnie tę samą odpowiedź co wcześniej.
         */
        if (replay.isPresent()) {
            IdempotencyRecord record = replay.get();

            return ResponseEntity
                    .status(record.getHttpStatus())
                    .body(record.getResponseBody());
        }

        /**
         * Tworzymy płatność tylko wtedy, gdy nie było wcześniejszego
         * rekordu idempotencji dla tego requestu.
         *
         * PaymentService wykona właściwą logikę:
         * - risk scoring,
         * - wybór PSP,
         * - stworzenie paymentu,
         * - wywołanie mockowego providera,
         * - zapis providerPaymentId i checkoutUrl.
         */
        PaymentResponse response = paymentService.create(request, key);

        /**
         * Serializujemy odpowiedź do JSON.
         *
         * Zapisujemy ją w tabeli idempotencji po to,
         * żeby kolejne identyczne requesty mogły dostać replay.
         */
        String body = objectMapper.writeValueAsString(response);

        /**
         * Zapisujemy wynik operacji idempotentnej.
         *
         * Zapamiętujemy:
         * - idempotency key,
         * - scope endpointu,
         * - hash requestu,
         * - body odpowiedzi,
         * - status HTTP.
         */
        idempotencyService.save(
                key,
                "POST:/v1/payments",
                canonical,
                body,
                HttpStatus.CREATED
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(body);
    }

    /**
     * Pobiera szczegóły pojedynczej płatności.
     *
     * Endpoint przydatny dla:
     * - frontendu checkoutu,
     * - panelu administracyjnego,
     * - procesu debugowania,
     * - sprawdzenia aktualnego statusu paymentu.
     *
     * @param paymentId ID płatności
     * @return aktualny stan płatności
     */
    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return paymentService.get(paymentId);
    }

    /**
     * Oznacza płatność jako zakończoną sukcesem.
     *
     * W realnym systemie taki endpoint zwykle nie byłby publicznym endpointem
     * dla użytkownika końcowego.
     *
     * Status SUCCEEDED najczęściej ustawiałby webhook od PSP,
     * który informuje nasz system, że płatność została opłacona.
     *
     * W tym projekcie endpoint służy do symulacji sukcesu płatności.
     *
     * @param paymentId ID płatności
     * @return płatność po zmianie statusu
     */
    @PostMapping("/{paymentId}/succeed")
    public PaymentResponse succeed(@PathVariable UUID paymentId) {
        return paymentService.markSucceeded(paymentId);
    }

    /**
     * Autoryzuje płatność.
     *
     * Authorization oznacza zablokowanie środków na instrumencie płatniczym
     * klienta bez ich finalnego pobrania.
     *
     * Przykład:
     * - hotel blokuje środki na karcie,
     * - wypożyczalnia samochodów blokuje depozyt,
     * - sklep najpierw sprawdza dostępność towaru, a dopiero później robi capture.
     *
     * Po autoryzacji płatność może zostać:
     * - przechwycona przez capture,
     * - anulowana przez cancel.
     *
     * @param paymentId ID płatności
     * @return płatność po autoryzacji
     */
    @PostMapping("/{paymentId}/authorize")
    public PaymentResponse authorize(@PathVariable UUID paymentId) {
        return paymentService.authorize(paymentId);
    }

    /**
     * Pobiera środki z wcześniej autoryzowanej płatności.
     *
     * Capture oznacza finalne pobranie pieniędzy.
     *
     * W manual capture flow najpierw wykonujemy authorize,
     * a dopiero później capture.
     *
     * Request zawiera amount, więc system może obsłużyć partial capture,
     * czyli pobranie tylko części wcześniej autoryzowanej kwoty.
     *
     * @param paymentId ID płatności
     * @param request kwota do pobrania
     * @return płatność po capture
     */
    @PostMapping("/{paymentId}/capture")
    public PaymentResponse capture(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CapturePaymentRequest request
    ) {
        return paymentService.capture(paymentId, request.amount());
    }

    /**
     * Anuluje płatność.
     *
     * Cancel ma sens głównie dla płatności autoryzowanych,
     * które nie zostały jeszcze przechwycone przez capture.
     *
     * Przykład:
     * - klient rezygnuje z zamówienia,
     * - merchant nie ma towaru,
     * - risk review odrzuca transakcję przed pobraniem środków.
     *
     * @param paymentId ID płatności
     * @return płatność po anulowaniu
     */
    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(@PathVariable UUID paymentId) {
        return paymentService.cancel(paymentId);
    }

    /**
     * Tworzy refund dla płatności.
     *
     * Refund oznacza zwrot środków do klienta po tym,
     * jak płatność została już pobrana.
     *
     * Ten endpoint przyjmuje opcjonalny Idempotency-Key.
     * W produkcyjnym systemie refund również powinien być idempotentny,
     * ponieważ podwójne wykonanie refundu mogłoby zwrócić klientowi
     * pieniądze dwa razy.
     *
     * Logika refundu znajduje się w PaymentService.
     * Serwis sprawdza między innymi:
     * - czy płatność istnieje,
     * - czy można ją refundować,
     * - jaka kwota została już zwrócona,
     * - czy request nie przekracza dostępnej kwoty,
     * - jakie księgowania trzeba wykonać w ledgerze.
     *
     * @param paymentId ID płatności, dla której robimy refund
     * @param key opcjonalny idempotency key dla refundu
     * @param request dane refundu, głównie kwota
     * @return utworzony refund
     */
    @PostMapping("/{paymentId}/refunds")
    public Refund refund(
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody RefundPaymentRequest request
    ) {
        return paymentService.refund(paymentId, request, key);
    }
}