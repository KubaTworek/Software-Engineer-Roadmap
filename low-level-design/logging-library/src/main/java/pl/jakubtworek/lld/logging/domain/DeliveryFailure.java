package pl.jakubtworek.lld.logging.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Niezmienny opis awarii bez referencji do Throwable i bez parsowania tekstu.
 * Optional zachowuje różnicę między null message i pustym komunikatem.
 */
public record DeliveryFailure(String exceptionType, Optional<String> message) {

    public DeliveryFailure {
        if (exceptionType == null || exceptionType.isBlank())
            throw new IllegalArgumentException("Pusty typ wyjątku");
        Objects.requireNonNull(message);
    }

    public static DeliveryFailure from(Exception exception) {
        Objects.requireNonNull(exception);
        return new DeliveryFailure(exception.getClass().getName(), Optional.ofNullable(exception.getMessage()));
    }
}
