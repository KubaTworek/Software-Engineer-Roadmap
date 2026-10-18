package pl.jakubtworek.marketplace.integration.outbox;

public enum OutboxEventStatus {
    NEW, PROCESSING, PUBLISHED, FAILED
}
