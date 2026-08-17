package pl.jakubtworek.chatsystem.realtime;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WsTypingRequest(
        @NotNull UUID conversationId
) {}
