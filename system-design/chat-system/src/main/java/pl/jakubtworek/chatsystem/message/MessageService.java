package pl.jakubtworek.chatsystem.message;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.conversation.ConversationRepository;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {
    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final UserRepository userRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationService conversationService,
            UserRepository userRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public MessageResponse sendMessage(UUID currentUserId, UUID conversationId, SendMessageRequest request) {
        conversationService.ensureMember(conversationId, currentUserId);

        return messageRepository.findBySenderIdAndClientMessageId(currentUserId, request.clientMessageId())
                .map(MessageResponse::from)
                .orElseGet(() -> createMessage(currentUserId, conversationId, request));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(UUID currentUserId, UUID conversationId, Instant before, int limit) {
        conversationService.ensureMember(conversationId, currentUserId);

        int pageSize = Math.max(1, Math.min(limit, MAX_LIMIT));
        PageRequest page = PageRequest.of(0, pageSize);

        List<Message> messages = before == null
                ? messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, page)
                : messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, before, page);

        return messages.stream()
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .map(MessageResponse::from)
                .toList();
    }

    private MessageResponse createMessage(UUID currentUserId, UUID conversationId, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        AppUser sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user not found"));

        Message message = new Message(conversation, sender, request.clientMessageId(), request.body().trim());
        Message saved = messageRepository.save(message);
        conversation.updateLastMessage(saved);
        conversationRepository.save(conversation);
        return MessageResponse.from(saved);
    }
}
