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
    Optional<Message> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    List<Message> findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID conversationId, Instant before, Pageable pageable);

    List<Message> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId, Instant after, Pageable pageable);

    @Query("""
            select distinct m from Message m
            join m.conversation c
            join c.members cm
            where cm.user.id = :viewerId
              and m.body is not null
              and lower(m.body) like :query
            order by m.createdAt desc
            """)
    List<Message> searchVisibleMessages(@Param("viewerId") UUID viewerId, @Param("query") String query, Pageable pageable);
}


