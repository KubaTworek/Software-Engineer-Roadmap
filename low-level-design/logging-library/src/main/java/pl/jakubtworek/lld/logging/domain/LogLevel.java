package pl.jakubtworek.lld.logging.domain;

/**
 * Priorytet jest jawną wartością, a nie skutkiem położenia enuma w pliku.
 * Próg jest inkluzywny: INFO przyjmuje INFO, WARN i ERROR.
 */
public enum LogLevel {

    TRACE(10), DEBUG(20), INFO(30), WARN(40), ERROR(50);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public boolean atLeast(LogLevel threshold) {
        return severity >= threshold.severity;
    }
}
