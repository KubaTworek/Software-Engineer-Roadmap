package pl.jakubtworek.chatsystem.conversation;

import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull ConversationRole role) {}
