package pl.jakubtworek.chatsystem.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface MessageSearchDocumentRepository extends JpaRepository<MessageSearchDocument, UUID> {
    /** ACL jest sprawdzane w source of truth przy każdym query; sam indeks nie jest autorytetem. */
    @Query("""
            select d from MessageSearchDocument d, ConversationMember cm
            where cm.conversation.id = d.conversationId
              and cm.user.id = :viewerId and cm.leftAt is null
              and d.sequenceNumber >= cm.joinedSequence
              and d.body is not null and lower(d.body) like :query
            order by d.sequenceNumber desc
            """)
    List<MessageSearchDocument> searchVisible(@Param("viewerId") UUID viewerId,
                                               @Param("query") String query, Pageable pageable);
}
