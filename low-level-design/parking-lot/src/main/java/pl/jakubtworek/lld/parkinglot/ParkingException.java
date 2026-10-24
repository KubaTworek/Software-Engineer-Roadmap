package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;

/**
 * Oczekiwana odmowa reguły parkingu. Stabilny kod pozwala adapterowi i testom
 * rozpoznać przyczynę bez parsowania polskiego komunikatu. Błąd konfiguracji,
 * awaria polityki lub przepełnienie obliczeń nie są udawanym sukcesem wyjazdu.
 */
public final class ParkingException extends RuntimeException {

    public enum Reason {

        VEHICLE_ALREADY_PARKED, NO_COMPATIBLE_SPOT, UNKNOWN_TICKET, TICKET_ALREADY_CLOSED, CLOCK_MOVED_BACKWARDS
    }

    private final Reason reason;

    public ParkingException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
