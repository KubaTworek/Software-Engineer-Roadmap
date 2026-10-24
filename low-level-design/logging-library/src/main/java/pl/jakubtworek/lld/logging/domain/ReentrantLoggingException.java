package pl.jakubtworek.lld.logging.domain;

/**
 * Ponowne wejście w aktywny logger jest błędem zależności, nie nowym zdarzeniem.
 * Ze środka sinka będzie FAILED; z przygotowania propaguje się przed zapisami.
 */
public final class ReentrantLoggingException extends IllegalStateException {

    public ReentrantLoggingException() {
        super("Rekurencyjne wywołanie tego samego loggera");
    }
}
