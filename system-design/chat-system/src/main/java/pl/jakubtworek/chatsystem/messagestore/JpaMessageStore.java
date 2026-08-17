package pl.jakubtworek.chatsystem.messagestore;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.message.MessageRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaMessageStore implements MessageStore {
    private final MessageRepository repository;

    public JpaMessageStore(MessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public Message save(Message message) {
        return repository.saveAndFlush(message);
    }

    @Override
    public Optional<Message> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Message> findBySenderAndClientMessageId(UUID senderId, UUID clientMessageId) {
        return repository.findBySenderIdAndClientMessageId(senderId, clientMessageId);
    }

    @Override
    public List<Message> latestForConversation(UUID conversationId, Pageable pageable) {
        return repository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
    }

    @Override
    public List<Message> before(UUID conversationId, Instant before, Pageable pageable) {
        return repository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, before, pageable);
    }

    @Override
    public List<Message> after(UUID conversationId, Instant after, Pageable pageable) {
        return repository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(conversationId, after, pageable);
    }
}
