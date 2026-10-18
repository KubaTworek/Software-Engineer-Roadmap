package pl.jakubtworek.chatsystem.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, UUID senderId, UUID clientMessageId);

    List<Message> findByConversationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberDesc(
            UUID conversationId, long joinedSequence, Pageable pageable);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.sequenceNumber >= :joined and m.sequenceNumber < :before order by m.sequenceNumber desc")
    List<Message> findVisibleBefore(@Param("conversationId") UUID conversationId, @Param("joined") long joined,
                                    @Param("before") long before, Pageable pageable);

    @Query("select m from Message m where m.conversation.id = :conversationId and m.sequenceNumber >= :joined and m.sequenceNumber > :after order by m.sequenceNumber asc")
    List<Message> findVisibleAfter(@Param("conversationId") UUID conversationId, @Param("joined") long joined,
                                   @Param("after") long after, Pageable pageable);

    @Query("select count(m) > 0 from Message m join m.attachments a join m.conversation.members cm " +
            "where a.id = :attachmentId and cm.user.id = :viewerId and cm.leftAt is null " +
            "and m.sequenceNumber >= cm.joinedSequence")
    boolean isAttachmentVisibleTo(@Param("attachmentId") UUID attachmentId, @Param("viewerId") UUID viewerId);

    @Query("""
            select distinct m from Message m
            join m.conversation c
            join c.members cm
            where cm.user.id = :viewerId
              and cm.leftAt is null
              and m.sequenceNumber >= cm.joinedSequence
              and m.body is not null
              and lower(m.body) like :query
            order by m.createdAt desc
            """)
    List<Message> searchVisibleMessages(@Param("viewerId") UUID viewerId, @Param("query") String query, Pageable pageable);
}


