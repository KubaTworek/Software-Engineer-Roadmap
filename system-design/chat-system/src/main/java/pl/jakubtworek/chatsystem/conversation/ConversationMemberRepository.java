package pl.jakubtworek.chatsystem.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {
    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("""
            select cm.conversation
            from ConversationMember cm
            left join fetch cm.conversation.lastMessage lm
            where cm.user.id = :userId
            order by cm.conversation.lastMessageAt desc nulls last, cm.conversation.createdAt desc
            """)
    List<Conversation> findConversationsForUser(@Param("userId") UUID userId);

    @Query("""
            select c
            from Conversation c
            join ConversationMember cm1 on cm1.conversation = c
            join ConversationMember cm2 on cm2.conversation = c
            where c.type = pl.jakubtworek.chatsystem.conversation.ConversationType.DIRECT
              and cm1.user.id = :userA
              and cm2.user.id = :userB
            """)
    Optional<Conversation> findDirectConversationBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("select cm from ConversationMember cm join fetch cm.user where cm.conversation.id = :conversationId")
    List<ConversationMember> findMembersByConversationId(@Param("conversationId") UUID conversationId);
}
