package pl.jakubtworek.lld.carrental;

import java.util.Objects;
import java.util.Optional;

/**
 * Kod odmowy jest kontraktem dla klienta, polski komunikat objaśnieniem dla człowieka.
 */
public final class RentalException extends RuntimeException {

    public enum Reason {

        UNKNOWN_VEHICLE,
        UNKNOWN_CUSTOMER,
        UNKNOWN_RESERVATION,
        UNKNOWN_MAINTENANCE,
        NOT_CUSTOMER,
        PAST_PERIOD,
        RESERVATION_CONFLICT,
        MAINTENANCE_CONFLICT,
        INVALID_STATUS,
        PICKUP_DATE_MISMATCH,
        VEHICLE_NOT_RETURNED
    }

    private final Reason reason;
    private final Optional<CalendarConflict> conflict;

    public RentalException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.conflict = Optional.empty();
    }

    /**
     * Kod pochodzi z wariantu konfliktu, więc nie może wskazywać innego rodzaju źródła.
     */
    public RentalException(CalendarConflict conflict) {
        super("Przedział koliduje z planem pojazdu");
        this.reason = Objects.requireNonNull(conflict, "conflict").reason();
        this.conflict = Optional.of(conflict);
    }

    public Reason reason() {
        return reason;
    }

    public Optional<CalendarConflict> conflict() {
        return conflict;
    }
}
