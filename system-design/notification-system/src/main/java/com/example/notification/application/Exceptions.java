package com.example.notification.application;

public final class Exceptions {
    private Exceptions() {}

    public static class NotificationValidationException extends RuntimeException {
        public NotificationValidationException(String message) { super(message); }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) { super(message); }
    }
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }

    public enum OutcomeCertainty { NOT_SENT, UNKNOWN }

    public static class ProviderCallException extends RuntimeException {
        private final OutcomeCertainty certainty;
        public ProviderCallException(String message, OutcomeCertainty certainty) { super(message); this.certainty = certainty; }
        public OutcomeCertainty certainty() { return certainty; }
    }

    public static class TransientProviderException extends RuntimeException {
        public TransientProviderException(String message) { super(message); }
    }

    /** Lokalny load shedding: nie wolno zmieniać providera i zwielokrotniać ruchu. */
    public static class AdmissionRejectedException extends RuntimeException {
        public AdmissionRejectedException(String message) { super(message); }
    }

    public static class PermanentProviderException extends RuntimeException {
        public PermanentProviderException(String message) { super(message); }
    }
}
