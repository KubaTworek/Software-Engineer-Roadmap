package pl.jakubtworek.lld.workflow.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Archiwalna pełna treść, autor zapisu i czas powstania. Tylko utworzenie/edycja
 * dodają rewizję; akceptacja, wycofanie i złożenie nie duplikują jej tekstu.
 */
public record ContentRevision(long number, DocumentContent content, UserId editor, Instant at) {

    public ContentRevision {
        if (number < 1)
            throw new IllegalArgumentException("Niepoprawny numer rewizji");
        Objects.requireNonNull(content);
        Objects.requireNonNull(editor);
        Objects.requireNonNull(at);
    }
}
