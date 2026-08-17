package com.example.ecommerce.payment;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.monitoring.BusinessMetrics;
import com.example.ecommerce.loyalty.LoyaltyService;
import com.example.ecommerce.notification.NotificationService;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.outbox.OutboxService;
import com.example.ecommerce.payment.dto.PaymentDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Serwis domenowy odpowiedzialny za płatności.
 *
 * W tej wersji projektu płatności są mockowane, ale flow biznesowy jest taki,
 * jak w realnym systemie e-commerce:
 *
 * - checkout tworzy płatność,
 * - sukces płatności opłaca zamówienie,
 * - inventory zostaje potwierdzone jako sprzedane,
 * - klient dostaje potwierdzenie,
 * - loyalty nalicza punkty,
 * - eventy trafiają do outboxa,
 * - metryki są aktualizowane.
 *
 * Przy porażce płatności:
 * - zamówienie przechodzi w PAYMENT_FAILED,
 * - rezerwacje inventory są zwalniane,
 * - stock wraca do sprzedaży,
 * - event PaymentFailed trafia do outboxa.
 */
@Service
public class PaymentService {

    /**
     * Repozytorium płatności.
     *
     * Przechowuje Payment powiązany z zamówieniem.
     */
    private final PaymentRepository payments;

    /**
     * Serwis inventory.
     *
     * Po sukcesie płatności potwierdza rezerwacje jako sprzedaż.
     * Po porażce płatności zwalnia rezerwacje.
     */
    private final InventoryService inventory;

    /**
     * Serwis notyfikacji.
     *
     * Po udanej płatności wysyła potwierdzenie zamówienia.
     *
     * W tej wersji może to być mock logujący e-mail,
     * a w produkcji adapter do e-mail/SMS/push.
     */
    private final NotificationService notifications;

    /**
     * Serwis outbox.
     *
     * Publikuje zdarzenia związane z płatnościami:
     * - PaymentCreated,
     * - PaymentSucceeded,
     * - PaymentFailed.
     *
     * Downstream services mogą na tej podstawie uruchomić ERP, CRM,
     * fulfillment, analitykę albo dodatkowe notyfikacje.
     */
    private final OutboxService outbox;

    /**
     * Metryki biznesowe.
     *
     * Pozwalają obserwować skuteczność płatności:
     * - sukcesy,
     * - porażki.
     */
    private final BusinessMetrics metrics;

    /**
     * Serwis loyalty.
     *
     * Po udanej płatności nalicza punkty użytkownikowi.
     *
     * Punkty powinny być naliczane dopiero po sukcesie płatności,
     * a nie przy samym utworzeniu zamówienia.
     */
    private final LoyaltyService loyalty;

    /**
     * Constructor injection.
     *
     * Uwaga:
     * W pokazanym kodzie jest pole loyalty, ale nie ma go w konstruktorze.
     * To spowoduje błąd kompilacji, bo pole final musi zostać zainicjalizowane.
     *
     * Poprawna wersja konstruktora powinna przyjmować LoyaltyService loyalty
     * i przypisać this.loyalty = loyalty.
     */
    public PaymentService(
            PaymentRepository payments,
            InventoryService inventory,
            NotificationService notifications,
            OutboxService outbox,
            BusinessMetrics metrics,
            LoyaltyService loyalty
    ) {
        this.payments = payments;
        this.inventory = inventory;
        this.notifications = notifications;
        this.outbox = outbox;
        this.metrics = metrics;
        this.loyalty = loyalty;
    }

    /**
     * Tworzy płatność dla zamówienia.
     *
     * Metoda jest idempotentna po idempotencyKey.
     *
     * Jeśli płatność z tym kluczem już istnieje, zwracamy istniejącą.
     * Jeśli nie istnieje, tworzymy nową płatność.
     *
     * To chroni przed utworzeniem wielu płatności dla tego samego checkoutu,
     * np. przy retry requestu.
     */
    @Transactional
    public Payment createPayment(CustomerOrder order, String idempotencyKey) {
        return payments.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> {
                    /*
                     * MOCK_PROVIDER oznacza, że nie ma jeszcze realnej integracji
                     * z operatorem płatności.
                     *
                     * W produkcji providerem mógłby być Stripe, PayU, Adyen itd.
                     */
                    Payment payment = payments.save(
                            new Payment(
                                    order,
                                    "MOCK_PROVIDER",
                                    order.getTotalAmount(),
                                    order.getCurrency(),
                                    idempotencyKey
                            )
                    );

                    /*
                     * Event PaymentCreated.
                     *
                     * Informuje system, że dla zamówienia powstała płatność
                     * oczekująca na finalny wynik.
                     */
                    outbox.saveEvent(
                            "Payment",
                            payment.getId().toString(),
                            "PaymentCreated",
                            Map.of(
                                    "paymentId", payment.getId(),
                                    "orderId", order.getId()
                            )
                    );

                    return payment;
                });
    }

    /**
     * Pobiera płatność po orderId.
     *
     * Używane np. przy idempotentnym retry checkoutu,
     * gdy trzeba zwrócić istniejącą płatność dla wcześniej utworzonego zamówienia.
     */
    public Payment getByOrderId(Long orderId) {
        return payments.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Payment not found for order"));
    }

    /**
     * Symuluje udaną płatność.
     *
     * Flow:
     * 1. Pobierz płatność.
     * 2. Jeśli już jest SUCCEEDED, zwróć obecny stan.
     * 3. Oznacz płatność jako SUCCEEDED.
     * 4. Oznacz zamówienie jako PAID.
     * 5. Potwierdź rezerwacje inventory.
     * 6. Wyślij potwierdzenie zamówienia.
     * 7. Nalicz punkty loyalty.
     * 8. Zwiększ metrykę sukcesu płatności.
     * 9. Zapisz event PaymentSucceeded.
     *
     * To jest mockowy odpowiednik webhooka success od operatora płatności.
     */
    @Transactional
    public PaymentDtos.PaymentResponse mockSuccess(Long paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        /*
         * Jeśli płatność już jest zakończona sukcesem,
         * nie wykonujemy skutków ubocznych drugi raz.
         *
         * To chroni przed podwójnym:
         * - potwierdzeniem inventory,
         * - wysłaniem maila,
         * - naliczeniem punktów loyalty,
         * - eventem PaymentSucceeded.
         */
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return toResponse(payment);
        }

        /*
         * Zapisujemy techniczny identyfikator transakcji providera.
         *
         * W mocku generujemy go lokalnie.
         * W produkcji byłoby to transactionId z providera płatności.
         */
        payment.markSucceeded("mock_" + UUID.randomUUID());

        /*
         * Zamówienie przechodzi w status opłacony.
         */
        payment.getOrder().markPaid();

        /*
         * Rezerwacje inventory zostają potwierdzone jako faktyczna sprzedaż.
         *
         * To zwykle zmniejsza availableQuantity i reservedQuantity.
         */
        inventory.confirmReservations(payment.getOrder().getId());

        /*
         * Wysyłamy potwierdzenie zamówienia.
         *
         * W obecnym projekcie NotificationService może być prostym mockiem,
         * ale miejsce integracji jest już dobrze wydzielone.
         */
        notifications.sendOrderConfirmation(payment.getOrder());

        /*
         * Punkty loyalty naliczamy dopiero po udanej płatności.
         *
         * To ważne, bo samo utworzenie zamówienia nie oznacza jeszcze,
         * że klient faktycznie zapłacił.
         */
        loyalty.earnForOrder(
                payment.getOrder().getUser(),
                payment.getOrder().getId(),
                payment.getOrder().getTotalAmount()
        );

        /*
         * Metryka sukcesu płatności.
         */
        metrics.paymentSucceeded();

        /*
         * Event PaymentSucceeded.
         *
         * Może uruchomić:
         * - ERP sync,
         * - fulfillment,
         * - notification-service,
         * - analytics,
         * - settlement marketplace.
         */
        outbox.saveEvent(
                "Payment",
                payment.getId().toString(),
                "PaymentSucceeded",
                Map.of(
                        "paymentId", payment.getId(),
                        "orderId", payment.getOrder().getId()
                )
        );

        return toResponse(payment);
    }

    /**
     * Symuluje nieudaną płatność.
     *
     * Flow:
     * 1. Pobierz płatność.
     * 2. Jeśli już jest FAILED, zwróć obecny stan.
     * 3. Oznacz płatność jako FAILED.
     * 4. Oznacz zamówienie jako PAYMENT_FAILED.
     * 5. Zwolnij rezerwacje inventory.
     * 6. Zwiększ metrykę porażki płatności.
     * 7. Zapisz event PaymentFailed.
     *
     * To jest mockowy odpowiednik webhooka failure od operatora płatności.
     */
    @Transactional
    public PaymentDtos.PaymentResponse mockFailure(Long paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        /*
         * Jeśli płatność już jest oznaczona jako failed,
         * nie zwalniamy inventory ani nie publikujemy eventu drugi raz.
         */
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return toResponse(payment);
        }

        /*
         * Mockowe oznaczenie płatności jako nieudanej.
         */
        payment.markFailed("mock_" + UUID.randomUUID());

        /*
         * Zamówienie przechodzi w status PAYMENT_FAILED.
         */
        payment.getOrder().markPaymentFailed();

        /*
         * Zwalniamy rezerwacje inventory.
         *
         * Stock wraca do sprzedaży, bo klient nie zapłacił.
         */
        inventory.releaseReservations(payment.getOrder().getId());

        /*
         * Metryka nieudanej płatności.
         */
        metrics.paymentFailed();

        /*
         * Event PaymentFailed.
         *
         * Może zostać użyty do:
         * - maila o nieudanej płatności,
         * - analityki konwersji,
         * - retry payment flow,
         * - synchronizacji statusu z ERP.
         */
        outbox.saveEvent(
                "Payment",
                payment.getId().toString(),
                "PaymentFailed",
                Map.of(
                        "paymentId", payment.getId(),
                        "orderId", payment.getOrder().getId()
                )
        );

        return toResponse(payment);
    }

    /**
     * Mapuje encję Payment na DTO odpowiedzi API.
     *
     * DTO zawiera:
     * - paymentId,
     * - orderId,
     * - provider,
     * - status,
     * - kwotę,
     * - walutę.
     *
     * Nie zwracamy encji JPA bezpośrednio na zewnątrz.
     */
    public PaymentDtos.PaymentResponse toResponse(Payment payment) {
        return new PaymentDtos.PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency()
        );
    }
}