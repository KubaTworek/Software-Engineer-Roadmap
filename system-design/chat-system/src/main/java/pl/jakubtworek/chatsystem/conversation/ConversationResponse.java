package pl.jakubtworek.chatsystem.conversation;

import pl.jakubtworek.chatsystem.message.MessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String title,
        UUID createdById,
        ConversationRole myRole,
        List<ConversationMemberResponse> members,
        MessageResponse lastMessage,
        long unreadCount,
        Instant lastReadAt,
        Instant createdAt,
        Instant updatedAt
) {}
