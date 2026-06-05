package pl.jakubtworek.chatsystem.conversation;

import pl.jakubtworek.chatsystem.message.MessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        List<ConversationMemberResponse> members,
        MessageResponse lastMessage,
        Instant createdAt,
        Instant updatedAt
) {}
