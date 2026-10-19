package pl.jakubtworek.chatsystem.search;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.message.*;
import java.util.*;

@Service
public class MessageSearchProjectionService {
    private final MessageSearchDocumentRepository documents;
    private final MessageRepository messages;
    public MessageSearchProjectionService(MessageSearchDocumentRepository documents, MessageRepository messages) {
        this.documents = documents; this.messages = messages;
    }
    /** Upsert po messageId + revision czyni redelivery zdarzenia bezpiecznym. */
    @Transactional
    public void project(Message message) {
        if (message.getDeletedAt() != null || message.getBody() == null) { documents.deleteById(message.getId()); return; }
        MessageSearchDocument document = documents.findById(message.getId())
                .orElseGet(() -> new MessageSearchDocument(message));
        document.updateFrom(message);
        documents.save(document);
    }
    @Transactional
    public void remove(UUID messageId) { documents.deleteById(messageId); }
    /** Rebuild czyta source of truth; w produkcji użyłby keyset pagination i checkpointu. */
    @Transactional
    public int rebuild() {
        documents.deleteAllInBatch();
        List<Message> source = messages.findAll();
        source.forEach(this::project);
        return source.size();
    }
    @Transactional(readOnly = true)
    public List<UUID> searchIds(UUID viewerId, String normalizedQuery, int limit) {
        return documents.searchVisible(viewerId, normalizedQuery, PageRequest.of(0, limit)).stream()
                .map(MessageSearchDocument::getMessageId).toList();
    }
}
