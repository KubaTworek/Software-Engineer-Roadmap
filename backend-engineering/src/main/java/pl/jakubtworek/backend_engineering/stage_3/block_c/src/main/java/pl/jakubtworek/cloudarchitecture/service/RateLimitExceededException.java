package pl.jakubtworek.cloudarchitecture.service;

/** Exception thrown when a client exceeds the configured request limit. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
