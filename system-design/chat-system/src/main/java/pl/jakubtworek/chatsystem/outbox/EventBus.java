package pl.jakubtworek.chatsystem.outbox;

public interface EventBus {
    void enqueue(OutboxEvent event);
}
