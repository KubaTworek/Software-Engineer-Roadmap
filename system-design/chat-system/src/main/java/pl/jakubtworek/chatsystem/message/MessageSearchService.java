package pl.jakubtworek.chatsystem.message;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MessageSearchService {
    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final MessageService messageService;

    public MessageSearchService(MessageRepository messageRepository, MessageService messageService) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> search(UUID currentUserId, String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            throw new BadRequestException("Search query must have at least 2 characters");
        }
        int pageSize = Math.max(1, Math.min(limit, MAX_LIMIT));
        String normalized = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        List<Message> messages = messageRepository.searchVisibleMessages(currentUserId, normalized, PageRequest.of(0, pageSize));
        return messageService.toResponsesForSearch(currentUserId, messages);
    }
}
