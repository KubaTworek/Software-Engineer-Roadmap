package pl.jakubtworek.chatsystem.conversation;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDirectConversationRequest(
        @NotNull UUID participantId
) {}
