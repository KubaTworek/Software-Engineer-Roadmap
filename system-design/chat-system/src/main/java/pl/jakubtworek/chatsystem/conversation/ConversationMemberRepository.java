package pl.jakubtworek.chatsystem.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {
    @Query("select count(cm) > 0 from ConversationMember cm where cm.conversation.id = :conversationId and cm.user.id = :userId and cm.leftAt is null")
    boolean existsByConversationIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("select cm from ConversationMember cm where cm.conversation.id = :conversationId and cm.user.id = :userId and cm.leftAt is null")
    Optional<ConversationMember> findByConversationIdAndUserId(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("select count(cm) from ConversationMember cm where cm.conversation.id = :conversationId and cm.leftAt is null")
    long countByConversationId(@Param("conversationId") UUID conversationId);

    @Query("select coalesce(max(cm.membershipEpoch), 0) from ConversationMember cm where cm.conversation.id = :conversationId and cm.user.id = :userId")
    int findLatestEpoch(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    @Query("""
            select cm.conversation
            from ConversationMember cm
            left join fetch cm.conversation.lastMessage lm
            where cm.user.id = :userId and cm.leftAt is null
            order by cm.conversation.lastMessageAt desc nulls last, cm.conversation.createdAt desc
            """)
    List<Conversation> findConversationsForUser(@Param("userId") UUID userId);

    @Query("""
            select c
            from Conversation c
            join ConversationMember cm1 on cm1.conversation = c
            join ConversationMember cm2 on cm2.conversation = c
            where c.type = pl.jakubtworek.chatsystem.conversation.ConversationType.DIRECT
              and cm1.user.id = :userA and cm1.leftAt is null
              and cm2.user.id = :userB and cm2.leftAt is null
            """)
    Optional<Conversation> findDirectConversationBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("select cm from ConversationMember cm join fetch cm.user where cm.conversation.id = :conversationId and cm.leftAt is null order by cm.joinedAt asc")
    List<ConversationMember> findMembersByConversationId(@Param("conversationId") UUID conversationId);

    @Query("select cm from ConversationMember cm join fetch cm.conversation where cm.user.id = :userId and cm.leftAt is null")
    List<ConversationMember> findMembershipsForUser(@Param("userId") UUID userId);
}
