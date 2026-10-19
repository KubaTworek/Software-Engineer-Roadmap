package pl.jakubtworek.chatsystem.outbox;

public interface DomainEventHandler {
    /** Stabilna nazwa konsumenta jest częścią danych inboxa; nie może zależeć od nazwy proxy Springa. */
    default String consumerName() { return getClass().getName(); }
    boolean supports(String eventType);
    void handle(OutboxEvent event);
}
