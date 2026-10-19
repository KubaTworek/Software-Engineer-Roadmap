package pl.jakubtworek.chatsystem.message;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.conversation.ConversationResponse;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.conversation.CreateDirectConversationRequest;
import pl.jakubtworek.chatsystem.outbox.OutboxEventRepository;
import pl.jakubtworek.chatsystem.outbox.OutboxStatus;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageServiceTest {
    @Autowired MessageService messageService;
    @Autowired ConversationService conversationService;
    @Autowired UserRepository userRepository;
    @Autowired MessageReceiptRepository receiptRepository;
    @Autowired OutboxEventRepository outboxEventRepository;

    @Test
    void sendMessageCreatesReceiptLastMessageAndOutboxEvent() {
        AppUser alice = TestUsers.create(userRepository, "alice_msg");
        AppUser bob = TestUsers.create(userRepository, "bob_msg");
        ConversationResponse conversation = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));

        MessageResponse response = messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(UUID.randomUUID(), "hello", List.of()));

        assertThat(response.status()).isEqualTo(MessageStatus.SENT);
        assertThat(response.body()).isEqualTo("hello");
        assertThat(receiptRepository.findByMessageIdAndRecipientId(response.id(), bob.getId())).isPresent();
        assertThat(conversationService.getConversation(bob.getId(), conversation.id()).unreadCount()).isEqualTo(1);
        assertThat(outboxEventRepository.countByStatus(OutboxStatus.NEW)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sendMessageIsDeduplicatedByClientMessageId() {
        AppUser alice = TestUsers.create(userRepository, "alice_dedup");
        AppUser bob = TestUsers.create(userRepository, "bob_dedup");
        ConversationResponse conversation = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        UUID clientMessageId = UUID.randomUUID();

        SendMessageResult first = messageService.sendMessageWithMetadata(alice.getId(), conversation.id(), new SendMessageRequest(clientMessageId, "hello", List.of()));
        SendMessageResult second = messageService.sendMessageWithMetadata(alice.getId(), conversation.id(), new SendMessageRequest(clientMessageId, "hello", List.of()));

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.message().id()).isEqualTo(first.message().id());
        assertThat(second.message().body()).isEqualTo("hello");
    }

    @Test
    void sameClientMessageIdWithDifferentPayloadIsAConflict() {
        AppUser alice = TestUsers.create(userRepository, "alice_conflict");
        AppUser bob = TestUsers.create(userRepository, "bob_conflict");
        ConversationResponse conversation = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        UUID key = UUID.randomUUID();
        messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(key, "original", List.of()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> messageService.sendMessage(
                alice.getId(), conversation.id(), new SendMessageRequest(key, "changed", List.of())))
                .isInstanceOf(pl.jakubtworek.chatsystem.common.ConflictException.class);
    }

    @Test
    void markReadUpToClearsUnreadCount() {
        AppUser alice = TestUsers.create(userRepository, "alice_read");
        AppUser bob = TestUsers.create(userRepository, "bob_read");
        ConversationResponse conversation = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        MessageResponse message = messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(UUID.randomUUID(), "read me", List.of()));

        ReceiptResponse receipt = messageService.markReadUpTo(bob.getId(), conversation.id(), message.id());

        assertThat(receipt.status()).isEqualTo(MessageStatus.READ);
        assertThat(conversationService.getConversation(bob.getId(), conversation.id()).unreadCount()).isZero();
    }

    @Test
    void historyPaginationReturnsHasMoreAndCursor() {
        AppUser alice = TestUsers.create(userRepository, "alice_page");
        AppUser bob = TestUsers.create(userRepository, "bob_page");
        ConversationResponse conversation = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(UUID.randomUUID(), "one", List.of()));
        messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(UUID.randomUUID(), "two", List.of()));
        messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(UUID.randomUUID(), "three", List.of()));

        MessagePageResponse page = messageService.getMessages(bob.getId(), conversation.id(), null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextBeforeSequence()).isNotNull();
    }

    @Test
    void editAndDeletePreserveSequenceAndExposeTombstoneDuringReconnect() {
        AppUser alice = TestUsers.create(userRepository, "alice_mutation");
        AppUser bob = TestUsers.create(userRepository, "bob_mutation");
        ConversationResponse conversation = conversationService.createDirectConversation(
                alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        MessageResponse original = messageService.sendMessage(alice.getId(), conversation.id(),
                new SendMessageRequest(UUID.randomUUID(), "draft", List.of()));

        MessageResponse edited = messageService.editMessage(alice.getId(), conversation.id(), original.id(),
                new EditMessageRequest("final text"));
        MessageResponse deleted = messageService.deleteMessage(alice.getId(), conversation.id(), original.id());
        List<MessageResponse> reconnect = messageService.getMessagesSince(bob.getId(), "bob-phone",
                conversation.id(), 0, 20);

        assertThat(edited.sequenceNumber()).isEqualTo(original.sequenceNumber());
        assertThat(edited.revision()).isEqualTo(2);
        assertThat(edited.editedAt()).isNotNull();
        assertThat(deleted.sequenceNumber()).isEqualTo(original.sequenceNumber());
        assertThat(deleted.revision()).isEqualTo(3);
        assertThat(deleted.body()).isNull();
        assertThat(deleted.deletedAt()).isNotNull();
        assertThat(reconnect).singleElement().satisfies(message -> {
            assertThat(message.id()).isEqualTo(original.id());
            assertThat(message.body()).isNull();
            assertThat(message.deletedAt()).isNotNull();
        });
    }
}
