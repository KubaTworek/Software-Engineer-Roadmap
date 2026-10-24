package pl.jakubtworek.lld.meetingrooms;

/**
 * Odmowa reguły domenowej ma stabilny kod; błędy konstrukcji wartości pozostają IllegalArgumentException.
 */
public final class BookingException extends RuntimeException {

    public enum Reason {

        PAST_START,
        NO_AVAILABLE_ROOM,
        UNKNOWN_BOOKING,
        NOT_ORGANIZER,
        NOT_CONFIRMED,
        ALREADY_STARTED
    }

    private final Reason reason;
    private final AvailabilityReport availability;

    public BookingException(Reason reason, String message) {
        this(reason, message, null);
    }

    /**
     * Szczegóły dotyczą odmowy dostępności; błędy aktora lub czasu nie mają raportu.
     */
    public BookingException(Reason reason, String message, AvailabilityReport availability) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
        this.availability = availability;
    }

    public Reason reason() {
        return reason;
    }

    public java.util.Optional<AvailabilityReport> availability() {
        return java.util.Optional.ofNullable(availability);
    }
}
