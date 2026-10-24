package pl.jakubtworek.lld.logging.format;

import pl.jakubtworek.lld.logging.domain.*;

/**
 * Stały mały schemat JSON bez bibliotek runtime. Escape dotyczy każdej wartości
 * tekstowej. Nie serializujemy dowolnych obiektów, wyjątków ani grafów danych.
 */
public final class JsonLogFormatter implements LogFormatter {

    public String format(LogEvent event) {
        String base = "{\"timestamp\":\"" + event.at() + "\",\"level\":\"" + event.level() + "\",\"logger\":\"" + Escaping.escape(event.loggerName()) + "\",\"message\":\"" + Escaping.escape(event.message()) + "\"";
        if (event.context().fields().isEmpty())
            return base + "}";
        var fields = new java.util.StringJoiner(",", ",\"context\":{", "}");
        event.context().fields().forEach((key,
                value) -> fields.add("\"" + Escaping.escape(key) + "\":\"" + Escaping.escape(value) + "\""));
        return base + fields + "}";
    }
}
