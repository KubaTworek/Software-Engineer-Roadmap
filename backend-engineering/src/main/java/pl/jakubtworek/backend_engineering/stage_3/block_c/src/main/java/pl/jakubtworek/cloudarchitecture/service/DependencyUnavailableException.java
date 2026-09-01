package pl.jakubtworek.cloudarchitecture.service;

/** Signals that an instance is alive but not ready because a critical dependency failed. */
public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
