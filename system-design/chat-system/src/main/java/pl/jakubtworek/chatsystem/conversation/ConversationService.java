package pl.jakubtworek.chatsystem.conversation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.message.MessageResponse;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ConversationResponse createDirectConversation(UUID currentUserId, CreateDirectConversationRequest request) {
        if (currentUserId.equals(request.participantId())) {
            throw new BadRequestException("Cannot create direct conversation with yourself");
        }

        AppUser currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user not found"));
        AppUser participant = userRepository.findById(request.participantId())
                .orElseThrow(() -> new NotFoundException("Participant not found"));

        return memberRepository.findDirectConversationBetween(currentUserId, participant.getId())
                .map(existing -> toResponse(existing, memberRepository.findMembersByConversationId(existing.getId())))
                .orElseGet(() -> {
                    Conversation conversation = new Conversation(ConversationType.DIRECT);
                    ConversationMember memberA = new ConversationMember(conversation, currentUser);
                    ConversationMember memberB = new ConversationMember(conversation, participant);
                    conversation.addMember(memberA);
                    conversation.addMember(memberB);
                    Conversation saved = conversationRepository.save(conversation);
                    return toResponse(saved, saved.getMembers());
                });
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID currentUserId) {
        return memberRepository.findConversationsForUser(currentUserId)
                .stream()
                .map(conversation -> toResponse(conversation, memberRepository.findMembersByConversationId(conversation.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID currentUserId, UUID conversationId) {
        ensureMember(conversationId, currentUserId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        return toResponse(conversation, memberRepository.findMembersByConversationId(conversationId));
    }

    public void ensureMember(UUID conversationId, UUID userId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a member of this conversation");
        }
    }

    private ConversationResponse toResponse(Conversation conversation, List<ConversationMember> members) {
        MessageResponse lastMessage = conversation.getLastMessage() == null
                ? null
                : MessageResponse.from(conversation.getLastMessage());

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                members.stream().map(ConversationMemberResponse::from).toList(),
                lastMessage,
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}
