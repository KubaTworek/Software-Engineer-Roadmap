package pl.jakubtworek.chatsystem.realtime;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WsReceiptRequest(
        @NotNull UUID conversationId,
        @NotNull UUID messageId
) {}
