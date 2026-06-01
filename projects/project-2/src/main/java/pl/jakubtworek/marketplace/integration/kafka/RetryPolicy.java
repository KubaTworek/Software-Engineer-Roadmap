package pl.jakubtworek.marketplace.integration.kafka;

public record RetryPolicy(int maxAttempts) {
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3);
    }
}
