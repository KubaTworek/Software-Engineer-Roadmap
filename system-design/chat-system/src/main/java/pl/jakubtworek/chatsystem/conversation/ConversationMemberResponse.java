package pl.jakubtworek.chatsystem.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationMemberResponse(
        UUID userId,
        String username,
        String displayName,
        ConversationRole role,
        Instant joinedAt,
        Instant lastReadAt,
        long joinedSequence,
        int membershipEpoch
) {
    public static ConversationMemberResponse from(ConversationMember member) {
        return new ConversationMemberResponse(
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getDisplayName(),
                member.getRole(),
                member.getJoinedAt(),
                member.getLastReadAt(),
                member.getJoinedSequence(),
                member.getMembershipEpoch()
        );
    }
}
