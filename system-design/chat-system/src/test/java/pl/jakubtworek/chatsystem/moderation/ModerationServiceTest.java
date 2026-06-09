package pl.jakubtworek.chatsystem.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.conversation.ConversationResponse;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.conversation.CreateDirectConversationRequest;
import pl.jakubtworek.chatsystem.message.MessageResponse;
import pl.jakubtworek.chatsystem.message.MessageService;
import pl.jakubtworek.chatsystem.message.SendMessageRequest;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModerationServiceTest {
    @Autowired ModerationService moderationService;
    @Autowired ConversationService conversationService;
    @Autowired MessageService messageService;
    @Autowired UserRepository userRepository;

    @Test
    void validateOutgoingMessageRejectsBannedTerms() {
        assertThatThrownBy(() -> moderationService.validateOutgoingMessage("please visit scam-link now"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reportMessageRequiresConversationMembershipAndCreatesOpenReport() {
        AppUser alice = TestUsers.create(userRepository, "alice_report");
        AppUser bob = TestUsers.create(userRepository, "bob_report");
        AppUser outsider = TestUsers.create(userRepository, "outsider_report");
        ConversationResponse conversation = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        MessageResponse message = messageService.sendMessage(alice.getId(), conversation.id(), new SendMessageRequest(UUID.randomUUID(), "bad message", List.of()));

        MessageReportResponse report = moderationService.reportMessage(bob.getId(), message.id(), new ReportMessageRequest("spam", "looks suspicious"));

        assertThat(report.status()).isEqualTo(ModerationStatus.OPEN);
        assertThat(report.messageId()).isEqualTo(message.id());
        assertThat(moderationService.getOpenReports()).extracting(MessageReportResponse::id).contains(report.id());
        assertThatThrownBy(() -> moderationService.reportMessage(outsider.getId(), message.id(), new ReportMessageRequest("spam", null)))
                .isInstanceOf(ForbiddenException.class);
    }
}
