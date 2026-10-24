package pl.jakubtworek.lld.carrental;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Rozliczenie modelu, nie dowód pobrania płatności. Total wyliczamy zamiast dublować sumę.
 */
public record RentalReceipt(ReservationId reservationId, LocalDate returnedOn, Money plannedCharge, Money lateCharge) {

    public RentalReceipt {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(returnedOn, "returnedOn");
        Objects.requireNonNull(plannedCharge, "plannedCharge");
        Objects.requireNonNull(lateCharge, "lateCharge");
        // Walidacja sumy teraz: obiekt nie może wejść do snapshotu, a dopiero
        // przy późniejszym odczycie ujawnić przepełnienia rozliczenia.
        plannedCharge.plus(lateCharge);
    }

    public Money total() {
        return plannedCharge.plus(lateCharge);
    }
}
