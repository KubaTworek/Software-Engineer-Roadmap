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
            PaymentStatus status
    ) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
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
                PaymentStatus.PENDING
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
        return new Payment(
                id,
                orderId,
                amount,
                status
        );
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
}