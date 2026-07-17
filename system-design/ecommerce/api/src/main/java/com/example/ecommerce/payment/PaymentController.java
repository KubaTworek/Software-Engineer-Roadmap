package com.example.ecommerce.payment;

import com.example.ecommerce.payment.dto.PaymentDtos;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za testową obsługę płatności.
 *
 * W tej wersji projektu nie ma jeszcze realnej integracji z operatorem płatności.
 * Zamiast tego udostępniamy dwa endpointy mockujące wynik płatności:
 * - sukces,
 * - porażkę.
 *
 * Te endpointy pozwalają przetestować cały dalszy flow po checkoutcie:
 * - zmianę statusu płatności,
 * - zmianę statusu zamówienia,
 * - potwierdzenie albo zwolnienie rezerwacji inventory,
 * - publikację eventów outbox,
 * - wysłanie notyfikacji,
 * - naliczenie punktów loyalty po udanej płatności.
 *
 * Produkcyjnie te endpointy zostałyby zastąpione webhookami
 * od providera płatności, np. Stripe, PayU, Przelewy24, Adyen.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    /**
     * Serwis płatności.
     *
     * Controller tylko przyjmuje request HTTP.
     * Cała logika zmiany statusu płatności i powiązanych procesów
     * znajduje się w PaymentService.
     */
    private final PaymentService payments;

    /**
     * Constructor injection.
     *
     * PaymentController potrzebuje tylko PaymentService,
     * bo nie powinien samodzielnie zmieniać statusów płatności ani zamówień.
     */
    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Symuluje udaną płatność.
     *
     * Endpoint:
     * POST /api/payments/{paymentId}/mock-success
     *
     * paymentId wskazuje płatność utworzoną wcześniej podczas checkoutu.
     *
     * Typowy efekt w PaymentService:
     * - Payment zmienia status na SUCCEEDED,
     * - CustomerOrder zmienia status na PAID,
     * - InventoryReservation zostaje potwierdzona,
     * - stock zostaje faktycznie pomniejszony,
     * - event PaymentSucceeded trafia do outboxa,
     * - klient może dostać potwierdzenie zamówienia,
     * - loyalty program może naliczyć punkty.
     *
     * To jest testowy odpowiednik pozytywnego webhooka od operatora płatności.
     */
    @PostMapping("/{paymentId}/mock-success")
    public PaymentDtos.PaymentResponse mockSuccess(@PathVariable Long paymentId) {
        return payments.mockSuccess(paymentId);
    }

    /**
     * Symuluje nieudaną płatność.
     *
     * Endpoint:
     * POST /api/payments/{paymentId}/mock-failure
     *
     * Typowy efekt w PaymentService:
     * - Payment zmienia status na FAILED,
     * - CustomerOrder zmienia status na PAYMENT_FAILED,
     * - aktywne rezerwacje inventory zostają zwolnione,
     * - stock wraca do puli dostępnej sprzedaży,
     * - event PaymentFailed trafia do outboxa.
     *
     * To pozwala przetestować scenariusz, w którym klient nie opłacił zamówienia
     * albo provider płatności odrzucił transakcję.
     */
    @PostMapping("/{paymentId}/mock-failure")
    public PaymentDtos.PaymentResponse mockFailure(@PathVariable Long paymentId) {
        return payments.mockFailure(paymentId);
    }
}