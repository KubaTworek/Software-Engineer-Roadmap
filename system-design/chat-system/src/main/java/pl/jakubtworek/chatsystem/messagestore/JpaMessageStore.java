package pl.jakubtworek.chatsystem.messagestore;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.message.MessageRepository;

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
    public Optional<Message> findByCommandKey(UUID conversationId, UUID senderId, UUID clientMessageId) {
        return repository.findByConversationIdAndSenderIdAndClientMessageId(conversationId, senderId, clientMessageId);
    }

    @Override
    public List<Message> latestForConversation(UUID conversationId, long joinedSequence, Pageable pageable) {
        return repository.findByConversationIdAndSequenceNumberGreaterThanEqualOrderBySequenceNumberDesc(
                conversationId, joinedSequence, pageable);
    }

    @Override
    public List<Message> beforeSequence(UUID conversationId, long joinedSequence, long before, Pageable pageable) {
        return repository.findVisibleBefore(conversationId, joinedSequence, before, pageable);
    }

    @Override
    public List<Message> afterSequence(UUID conversationId, long joinedSequence, long after, Pageable pageable) {
        return repository.findVisibleAfter(conversationId, joinedSequence, after, pageable);
    }
}
