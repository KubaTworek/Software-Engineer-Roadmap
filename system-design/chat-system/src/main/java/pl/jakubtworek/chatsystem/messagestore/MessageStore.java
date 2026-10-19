package pl.jakubtworek.chatsystem.messagestore;

import org.springframework.data.domain.Pageable;
import pl.jakubtworek.chatsystem.message.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage boundary for messages.
 *
 * The reference implementation uses JPA by default so it remains easy to run locally, but the application no longer has to treat
 * PostgreSQL/H2 as the only possible message database. A production implementation can replace this adapter with
 * Cassandra, ScyllaDB, DynamoDB or another append-optimized message store.
 */
public interface MessageStore {
    Message save(Message message);
    Optional<Message> findById(UUID id);
    Optional<Message> findByCommandKey(UUID conversationId, UUID senderId, UUID clientMessageId);
    List<Message> latestForConversation(UUID conversationId, long joinedSequence, Pageable pageable);
    List<Message> beforeSequence(UUID conversationId, long joinedSequence, long before, Pageable pageable);
    List<Message> afterSequence(UUID conversationId, long joinedSequence, long after, Pageable pageable);
}
