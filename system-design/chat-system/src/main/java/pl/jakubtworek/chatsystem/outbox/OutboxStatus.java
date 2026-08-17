package pl.jakubtworek.chatsystem.outbox;

public enum OutboxStatus {
    NEW,
    ENQUEUED,
    PUBLISHED,
    FAILED
}
