package pl.jakubtworek.lld.logging.format;

import pl.jakubtworek.lld.logging.domain.*;

/**
 * Czytelny format bez separatora końcowego. Kodujemy także nazwę loggera:
 * dane wejściowe nie mogą wstawić fizycznej nowej linii do rekordu.
 */
public final class PlainTextFormatter implements LogFormatter {

    public String format(LogEvent event) {
        String base = event.at() + " " + event.level() + " [" + Escaping.escape(event.loggerName()) + "] " + Escaping.escape(event.message());
        if (event.context().fields().isEmpty())
            return base;
        var fields = new java.util.StringJoiner(",", " context={", "}");
        event.context().fields().forEach((key, value) -> fields.add(key + "=\"" + Escaping.escape(value) + "\""));
        return base + fields;
    }
}
