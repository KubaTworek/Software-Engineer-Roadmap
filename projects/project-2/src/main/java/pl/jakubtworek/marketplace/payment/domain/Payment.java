package pl.jakubtworek.marketplace.payment.domain;

import pl.jakubtworek.marketplace.shared.kernel.AggregateRoot;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

/**
 * Agregat reprezentujący płatność dla zamówienia.
 *
 * Payment należy do domeny modułu Payment. Odpowiada za przechowywanie stanu płatności
 * oraz rejestrowanie zdarzeń wynikających z próby rezerwacji płatności.
 *
 * Ta klasa nie powinna zależeć od:
 * - Springa,
 * - JPA,
 * - JDBC,
 * - HTTP,
 * - Kafki,
 * - konkretnej bramki płatności.
 *
 * Integracja z zewnętrzną bramką płatności jest realizowana poza domeną,
 * przez port PaymentGateway oraz jego adapter infrastrukturalny.
 */
public class Payment extends AggregateRoot {

    /**
     * Tożsamość płatności.
     *
     * PaymentId jest osobnym value objectem, żeby nie przekazywać po domenie surowego UUID.
     */
    private final PaymentId id;

    /**
     * Identyfikator zamówienia, którego dotyczy płatność.
     *
     * W module Payment używamy UUID zamiast OrderId z modułu Ordering, żeby nie sprzęgać
     * domeny Payment z domeną Ordering. Payment wie tylko, że płatność dotyczy konkretnego
     * zamówienia identyfikowanego przez UUID.
     */
    private final UUID orderId;

    /**
     * Kwota płatności.
     *
     * Money jest value objectem zawierającym kwotę oraz walutę.
     * Poprawność kwoty i waluty powinna być pilnowana wewnątrz Money.
     */
    private final Money amount;

    /**
     * Aktualny status płatności.
     *
     * Status zmienia się w wyniku operacji domenowych, np. rezerwacji płatności.
     */
    private PaymentStatus status;
    private final String providerOperationId;
    private final String uncertaintyReason;

    /**
     * Prywatny konstruktor wymusza tworzenie agregatu przez metody fabrykujące.
     *
     * Dzięki temu zewnętrzny kod nie może przypadkowo utworzyć płatności
     * w niepoprawnym stanie.
     */
    private Payment(
            PaymentId id,
            UUID orderId,
            Money amount,
            PaymentStatus status,
            String providerOperationId,
            String uncertaintyReason
    ) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.providerOperationId = providerOperationId;
        this.uncertaintyReason = uncertaintyReason;
    }

    /**
     * Tworzy płatność jako wynik próby rezerwacji środków.
     *
     * Ta metoda reprezentuje decyzję domenową po otrzymaniu wyniku z PaymentGateway.
     *
     * Jeśli accepted == true:
     * - płatność przechodzi do statusu RESERVED,
     * - rejestrowane jest zdarzenie PaymentReserved.
     *
     * Jeśli accepted == false:
     * - płatność przechodzi do statusu REJECTED,
     * - rejestrowane jest zdarzenie PaymentRejected.
     *
     * correlationId pozwala śledzić cały flow zamówienia.
     * causationId wskazuje zdarzenie, które spowodowało powstanie tej płatności,
     * najczęściej OrderPlaced.eventId().
     */
    public static Payment reserve(
            UUID orderId,
            Money amount,
            boolean accepted,
            UUID correlationId,
            UUID causationId
    ) {
        Payment payment = new Payment(
                PaymentId.newId(),
                orderId,
                amount,
                PaymentStatus.PENDING,
                null,
                null
        );

        if (accepted) {
            payment.status = PaymentStatus.RESERVED;
            payment.registerEvent(PaymentReserved.now(
                    payment,
                    correlationId,
                    causationId
            ));
        } else {
            payment.status = PaymentStatus.REJECTED;
            payment.registerEvent(PaymentRejected.now(
                    payment,
                    "Payment gateway rejected reservation",
                    correlationId,
                    causationId
            ));
        }

        return payment;
    }

    /**
     * Timeout po wysłaniu requestu nie jest odrzuceniem. Provider mógł wykonać
     * autoryzację, dlatego stan UNKNOWN blokuje zarówno sukces sagi, jak i ponowne
     * obciążenie. Jedyną bezpieczną następną operacją jest lookup po stabilnym ID.
     */
    public static Payment uncertain(UUID orderId, Money amount, String providerOperationId, String reason) {
        if (providerOperationId == null || providerOperationId.isBlank()) {
            throw new IllegalArgumentException("unknown payment needs provider operation id");
        }
        return new Payment(PaymentId.newId(), orderId, amount, PaymentStatus.UNKNOWN,
                providerOperationId, reason);
    }

    public void resolve(boolean accepted, UUID correlationId, UUID causationId, String reason) {
        if (status != PaymentStatus.UNKNOWN) return;
        status = accepted ? PaymentStatus.RESERVED : PaymentStatus.REJECTED;
        registerEvent(accepted
                ? PaymentReserved.now(this, correlationId, causationId)
                : PaymentRejected.now(this, reason, correlationId, causationId));
    }

    /**
     * Odtwarza płatność z istniejącego stanu, np. z bazy danych.
     *
     * Ta metoda nie oznacza biznesowej rezerwacji płatności.
     * Nie generuje PaymentReserved ani PaymentRejected.
     *
     * Służy wyłącznie do rekonstrukcji agregatu na podstawie danych zapisanych wcześniej.
     */
    public static Payment restore(
            PaymentId id,
            UUID orderId,
            Money amount,
            PaymentStatus status
    ) {
        return restore(id, orderId, amount, status, null, null);
    }

    public static Payment restore(PaymentId id, UUID orderId, Money amount, PaymentStatus status,
                                  String providerOperationId, String uncertaintyReason) {
        return new Payment(id, orderId, amount, status, providerOperationId, uncertaintyReason);
    }

    /**
     * Zwalnia autoryzację jako kompensację sagi. Operacja jest idempotentna:
     * ponowne dostarczenie komendy nie wywołuje drugiego efektu. W projekcie status
     * REFUNDED oznacza zarówno zwolnienie autoryzacji, jak i zwrot po capture;
     * produkcyjny model zwykle rozdziela te operacje.
     */
    public boolean release() {
        if (status == PaymentStatus.REFUNDED) return false;
        if (status != PaymentStatus.RESERVED && status != PaymentStatus.CAPTURED) {
            throw new IllegalStateException("Only reserved or captured payment can be released");
        }
        status = PaymentStatus.REFUNDED;
        return true;
    }

    /**
     * Zwraca identyfikator płatności.
     */
    public PaymentId id() {
        return id;
    }

    /**
     * Zwraca identyfikator zamówienia, którego dotyczy płatność.
     */
    public UUID orderId() {
        return orderId;
    }

    /**
     * Zwraca kwotę płatności.
     */
    public Money amount() {
        return amount;
    }

    /**
     * Zwraca aktualny status płatności.
     */
    public PaymentStatus status() {
        return status;
    }

    public String providerOperationId() { return providerOperationId; }
    public String uncertaintyReason() { return uncertaintyReason; }
}
