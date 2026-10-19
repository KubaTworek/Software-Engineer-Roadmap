package pl.jakubtworek.chatsystem.conversation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.media.*;
import pl.jakubtworek.chatsystem.message.*;
import pl.jakubtworek.chatsystem.search.MessageSearchProjectionService;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemporalMembershipTest {
    @Autowired UserRepository users;
    @Autowired ConversationService conversations;
    @Autowired MessageService messages;
    @Autowired MessageSearchService search;
    @Autowired MessageSearchProjectionService projection;
    @Autowired AttachmentService attachmentService;
    @Autowired AttachmentScanWorker scanWorker;

    @Test
    void rejoiningStartsNewAclEpochAndDoesNotRestoreOldHistorySearchOrFiles() {
        AppUser owner = TestUsers.create(users, "history_owner");
        AppUser member = TestUsers.create(users, "history_member");
        UUID conversationId = conversations.createGroupConversation(owner.getId(),
                new CreateGroupConversationRequest("temporal ACL", Set.of(member.getId()))).id();

        AttachmentResponse upload = attachmentService.createUpload(owner.getId(),
                new CreateAttachmentRequest("before.txt", "text/plain", 6));
        attachmentService.uploadContent(upload.id(), upload.uploadToken(), "secret".getBytes(StandardCharsets.UTF_8));
        scanWorker.scan(upload.id());
        messages.sendMessage(owner.getId(), conversationId,
                new SendMessageRequest(UUID.randomUUID(), "old-secret-message", List.of(upload.id())));

        conversations.removeMember(owner.getId(), conversationId, member.getId());
        messages.sendMessage(owner.getId(), conversationId,
                new SendMessageRequest(UUID.randomUUID(), "during-absence-message", List.of()));
        conversations.addMember(owner.getId(), conversationId,
                new AddGroupMemberRequest(member.getId(), ConversationRole.MEMBER));
        messages.sendMessage(owner.getId(), conversationId,
                new SendMessageRequest(UUID.randomUUID(), "new-visible-message", List.of()));
        projection.rebuild();

        assertThat(messages.getMessages(member.getId(), conversationId, null, 50).items())
                .extracting(MessageResponse::body).containsExactly("new-visible-message");
        assertThat(search.search(member.getId(), "message", 50))
                .extracting(MessageResponse::body).containsExactly("new-visible-message");
        assertThatThrownBy(() -> attachmentService.downloadContent(member.getId(), upload.id()))
                .isInstanceOf(ForbiddenException.class);
    }
}
