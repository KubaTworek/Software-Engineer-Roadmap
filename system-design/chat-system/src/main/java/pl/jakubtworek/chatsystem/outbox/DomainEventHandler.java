package pl.jakubtworek.chatsystem.outbox;

public interface DomainEventHandler {
    boolean supports(String eventType);
    void handle(OutboxEvent event);
}
