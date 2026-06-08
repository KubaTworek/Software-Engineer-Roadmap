package com.example.notification.application;

public final class Exceptions {
    private Exceptions() {}

    public static class NotificationValidationException extends RuntimeException {
        public NotificationValidationException(String message) { super(message); }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) { super(message); }
    }

    public static class TransientProviderException extends RuntimeException {
        public TransientProviderException(String message) { super(message); }
    }

    public static class PermanentProviderException extends RuntimeException {
        public PermanentProviderException(String message) { super(message); }
    }
}
