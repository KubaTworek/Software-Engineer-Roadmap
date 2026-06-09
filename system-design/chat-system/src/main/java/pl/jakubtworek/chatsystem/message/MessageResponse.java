package pl.jakubtworek.chatsystem.message;

import pl.jakubtworek.chatsystem.media.AttachmentResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String senderUsername,
        UUID clientMessageId,
        String body,
        List<AttachmentResponse> attachments,
        MessageStatus status,
        Instant deliveredAt,
        Instant readAt,
        Instant createdAt
) {
    public static MessageResponse from(Message message) {
        return from(message, MessageStatus.SENT, null, null);
    }

    public static MessageResponse from(Message message, MessageStatus status, Instant deliveredAt, Instant readAt) {
        List<AttachmentResponse> attachments = message.getAttachments().stream()
                .map(attachment -> AttachmentResponse.from(
                        attachment,
                        null,
                        attachment.getStatus().name().equals("UPLOADED") ? "/api/attachments/" + attachment.getId() + "/content" : null
                ))
                .toList();
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getClientMessageId(),
                message.getBody(),
                attachments,
                status,
                deliveredAt,
                readAt,
                message.getCreatedAt()
        );
    }
}
