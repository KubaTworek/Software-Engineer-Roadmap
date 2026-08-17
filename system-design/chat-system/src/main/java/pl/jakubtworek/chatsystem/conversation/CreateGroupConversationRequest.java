package pl.jakubtworek.chatsystem.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateGroupConversationRequest(
        @NotBlank @Size(max = 120) String title,
        @NotNull @Size(min = 1, max = 100) Set<UUID> participantIds
) {}
