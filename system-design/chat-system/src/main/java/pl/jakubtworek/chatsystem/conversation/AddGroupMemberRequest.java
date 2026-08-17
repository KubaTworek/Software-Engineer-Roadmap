package pl.jakubtworek.chatsystem.conversation;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddGroupMemberRequest(
        @NotNull UUID userId,
        ConversationRole role
) {}
