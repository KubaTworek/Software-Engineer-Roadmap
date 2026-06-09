package pl.jakubtworek.chatsystem.production;

public record ProductionStatusResponse(
        long outboxNew,
        long outboxFailed,
        int localQueueSize,
        String messageStore,
        String eventBus
) {}
