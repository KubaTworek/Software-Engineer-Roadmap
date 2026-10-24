package pl.jakubtworek.lld.carrental;

import java.util.Objects;
import java.util.UUID;

/**
 * Jedno zawarcie umowy, a nie para klient–auto. Powtórne wypożyczenie tej samej
 * osobie ma nowe ID: stare polecenie zwrotu nie zamknie nowego wynajmu.
 * UUID nie zapewnia autoryzacji ani pełnej idempotencji poleceń.
 */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "value");
    }

    public static ReservationId parse(String value) {
        return new ReservationId(UUID.fromString(Text.nonBlank(value)));
    }
}
