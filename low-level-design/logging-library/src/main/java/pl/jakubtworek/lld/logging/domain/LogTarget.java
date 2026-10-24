package pl.jakubtworek.lld.logging.domain;

import java.util.Objects;

/**
 * Nazwana trasa z progiem i wyjściem. Nazwa identyfikuje pozycję w raporcie,
 * a nie zasób systemowy. Sink może mieć stan; rekord nie zamraża implementacji sinka.
 */
public record LogTarget(String name, LogLevel threshold, LogSink sink) {

    public LogTarget {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Pusta nazwa wyjścia");
        Objects.requireNonNull(threshold);
        Objects.requireNonNull(sink);
    }
}
