package pl.jakubtworek.lld.logging.sink;

import java.io.IOException;

/**
 * Jawna odmowa wyjścia przed zapisem nowego rekordu, obsługiwana przez FailurePolicy.
 */
public final class SinkCapacityException extends IOException {

    public SinkCapacityException(int capacity) {
        super("Pełny bufor pamięciowy, capacity=" + capacity);
    }
}
