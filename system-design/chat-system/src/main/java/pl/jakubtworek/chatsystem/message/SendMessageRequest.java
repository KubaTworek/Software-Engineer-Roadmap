package pl.jakubtworek.chatsystem.message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SendMessageRequest(
        @NotNull UUID clientMessageId,
        @Size(max = 4000) String body,
        List<UUID> attachmentIds
) {}
