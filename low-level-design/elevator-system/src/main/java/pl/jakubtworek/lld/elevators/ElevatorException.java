package pl.jakubtworek.lld.elevators;

import java.util.Objects;

/**
 * Kod rozróżnia odmowę reguły od wadliwej konfiguracji i błędu programisty; adapter nie parsuje treści komunikatu.
 */
public final class ElevatorException extends RuntimeException {

    public enum Reason {

        INVALID_FLOOR,
        UNKNOWN_ELEVATOR,
        NO_AVAILABLE_ELEVATOR,
        OUT_OF_SERVICE,
        DRAINING,
        NOT_IDLE,
        ALREADY_DRAINING,
        ALREADY_OUT_OF_SERVICE,
        ALREADY_IN_SERVICE
    }

    private final Reason reason;

    public ElevatorException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
