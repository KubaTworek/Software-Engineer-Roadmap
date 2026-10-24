package pl.jakubtworek.lld.parkinglot;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object z danymi potrzebnymi do wyceny: rodzaj pojazdu i miejsca oraz
 * poprawny przedział czasu. Nie ujawnia numeru biletu, rejestracji, rachunku
 * ani cyklu życia. Polityka ceny nie musi zależeć od całej encji postoju.
 *
 * Cel edukacyjny: poprawność przedziału sprawdzamy raz przy tworzeniu wartości,
 * zamiast odmiennie w każdym cenniku. Publiczny kontekst pozwala też niezależnie
 * wycenić hipotetyczny postój, ale nie rezerwuje miejsca i nie zamyka biletu.
 * Zgodność pojazdu z miejscem oraz aktywność biletu nadal chroni ParkingLot.
 */
public record PricingContext(VehicleType vehicleType, SpotType spotType, Instant enteredAt, Instant exitedAt) {

    public PricingContext {
        Objects.requireNonNull(vehicleType, "vehicleType");
        Objects.requireNonNull(spotType, "spotType");
        Objects.requireNonNull(enteredAt, "enteredAt");
        Objects.requireNonNull(exitedAt, "exitedAt");
        if (exitedAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("Przedział wyceny nie może kończyć się przed wjazdem");
        }
    }

    public Duration duration() {
        return Duration.between(enteredAt, exitedAt);
    }
}
