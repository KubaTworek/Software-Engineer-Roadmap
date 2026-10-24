package pl.jakubtworek.lld.parkinglot;

import java.time.Instant;
import java.util.Objects;

/**
 * Niemutowalne rozliczenie zakończonego postoju. Zachowujemy kwotę wyliczoną
 * przy wyjeździe, zamiast później przeliczać historyczny bilet nowym cennikiem.
 * Dokument oznacza należność w symulowanym wyjeździe, NIE potwierdzenie pobrania
 * pieniędzy przez bank. ParkingLot nie przyjmuje zewnętrznego rachunku do zapisu.
 */
public record ParkingReceipt(TicketId ticketId, Instant enteredAt, Instant exitedAt, Money fee) {

    public ParkingReceipt {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(enteredAt, "enteredAt");
        Objects.requireNonNull(exitedAt, "exitedAt");
        Objects.requireNonNull(fee, "fee");
        if (exitedAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("Wyjazd nie może poprzedzać wjazdu");
        }
    }
}
