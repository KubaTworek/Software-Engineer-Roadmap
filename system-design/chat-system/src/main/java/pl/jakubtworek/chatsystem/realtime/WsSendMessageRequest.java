package pl.jakubtworek.chatsystem.realtime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record WsSendMessageRequest(
        @NotNull UUID conversationId,
        @NotNull UUID clientMessageId,
        @Size(max = 4000) String body,
        List<UUID> attachmentIds
) {}
