package pl.jakubtworek.chatsystem.conversation;

import java.util.UUID;

public record ConversationMemberResponse(
        UUID userId,
        String username,
        String displayName
) {
    public static ConversationMemberResponse from(ConversationMember member) {
        return new ConversationMemberResponse(
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getDisplayName()
        );
    }
}
