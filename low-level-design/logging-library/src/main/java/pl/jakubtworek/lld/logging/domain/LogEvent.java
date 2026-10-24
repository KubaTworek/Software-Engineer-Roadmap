package pl.jakubtworek.lld.logging.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Niezmienny materiał wspólny dla wszystkich wyjść jednej operacji. Tekst jest
 * String, nie mutowalnym Throwable lub Map<Object,Object>. Pusty komunikat
 * jest poprawny, ale brak komunikatu nie. Timestamp nie wyznacza kolejności.
 */
public record LogEvent(Instant at, LogLevel level, String loggerName, String message, LogContext context) {

    public LogEvent(Instant at, LogLevel level, String loggerName, String message) {
        this(at, level, loggerName, message, LogContext.empty());
    }

    public LogEvent {
        Objects.requireNonNull(context);
        Objects.requireNonNull(at);
        Objects.requireNonNull(level);
        Objects.requireNonNull(message);
        if (loggerName == null || loggerName.isBlank())
            throw new IllegalArgumentException("Pusta nazwa loggera");
    }
}
