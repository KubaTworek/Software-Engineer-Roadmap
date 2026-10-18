package pl.jakubtworek.chatsystem.message;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.search.MessageSearchProjectionService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class MessageSearchService {
    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final MessageSearchProjectionService projection;

    public MessageSearchService(MessageRepository messageRepository, MessageService messageService,
                                MessageSearchProjectionService projection) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.projection = projection;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> search(UUID currentUserId, String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            throw new BadRequestException("Search query must have at least 2 characters");
        }
        int pageSize = Math.max(1, Math.min(limit, MAX_LIMIT));
        String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        List<UUID> ids = projection.searchIds(currentUserId, normalized, pageSize);
        List<Message> messages = ids.stream().map(messageRepository::findById).flatMap(Optional::stream).toList();
        return messageService.toResponsesForSearch(currentUserId, messages);
    }
}
