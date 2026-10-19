package pl.jakubtworek.chatsystem.media;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.conversation.ConversationResponse;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.conversation.CreateDirectConversationRequest;
import pl.jakubtworek.chatsystem.message.MessageService;
import pl.jakubtworek.chatsystem.message.SendMessageRequest;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttachmentServiceTest {
    @Autowired AttachmentService attachmentService;
    @Autowired UserRepository userRepository;
    @Autowired ConversationService conversationService;
    @Autowired MessageService messageService;
    @Autowired AttachmentRepository attachments;
    @Autowired AttachmentScanWorker scanWorker;

    @Test
    void createUploadThenUploadContentAndDownloadAsOwner() {
        AppUser owner = TestUsers.create(userRepository, "owner_attachment");

        AttachmentResponse created = attachmentService.createUpload(
                owner.getId(),
                new CreateAttachmentRequest("note.txt", "text/plain", 5)
        );
        assertThat(created.status()).isEqualTo(AttachmentStatus.UPLOAD_PENDING);
        String uploadToken = created.uploadToken();
        AttachmentResponse scanning = attachmentService.uploadContent(created.id(), uploadToken, "hello".getBytes(StandardCharsets.UTF_8));
        assertThat(scanning.status()).isEqualTo(AttachmentStatus.SCANNING);
        scanWorker.scan(created.id());
        AttachmentResponse uploaded = AttachmentResponse.from(attachments.findById(created.id()).orElseThrow(), null,
                "/api/attachments/" + created.id() + "/content");

        assertThat(uploaded.status()).isEqualTo(AttachmentStatus.READY);
        assertThat(uploaded.downloadUrl()).isEqualTo("/api/attachments/" + created.id() + "/content");
        assertThat(new String(attachmentService.downloadContent(owner.getId(), created.id()), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void validatesMetadataAndOwnership() {
        AppUser owner = TestUsers.create(userRepository, "owner_attachment_validation");
        AppUser other = TestUsers.create(userRepository, "other_attachment_validation");

        assertThatThrownBy(() -> attachmentService.createUpload(owner.getId(), new CreateAttachmentRequest("../evil.txt", "text/plain", 1)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> attachmentService.createUpload(owner.getId(), new CreateAttachmentRequest("evil.exe", "application/x-msdownload", 1)))
                .isInstanceOf(BadRequestException.class);

        AttachmentResponse created = attachmentService.createUpload(owner.getId(), new CreateAttachmentRequest("image.png", "image/png", 4));
        String uploadToken = created.uploadToken();
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        attachmentService.uploadContent(created.id(), uploadToken, png);
        scanWorker.scan(created.id());
        AttachmentResponse uploaded = AttachmentResponse.from(attachments.findById(created.id()).orElseThrow(), null,
                "/api/attachments/" + created.id() + "/content");

        assertThat(attachmentService.getReadyAttachmentsOwnedBy(owner.getId(), List.of(uploaded.id()))).hasSize(1);
        assertThatThrownBy(() -> attachmentService.getReadyAttachmentsOwnedBy(other.getId(), List.of(uploaded.id())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void recipientCanDownloadOnlyAfterAttachmentWasSentInVisibleConversation() {
        AppUser owner = TestUsers.create(userRepository, "attachment_sender");
        AppUser recipient = TestUsers.create(userRepository, "attachment_recipient");
        AppUser outsider = TestUsers.create(userRepository, "attachment_outsider");
        AttachmentResponse created = attachmentService.createUpload(owner.getId(),
                new CreateAttachmentRequest("note.txt", "text/plain", 5));
        attachmentService.uploadContent(created.id(), created.uploadToken(), "hello".getBytes(StandardCharsets.UTF_8));
        scanWorker.scan(created.id());
        AttachmentResponse ready = AttachmentResponse.from(attachments.findById(created.id()).orElseThrow(), null,
                "/api/attachments/" + created.id() + "/content");

        assertThatThrownBy(() -> attachmentService.downloadContent(recipient.getId(), ready.id()))
                .isInstanceOf(ForbiddenException.class);

        ConversationResponse conversation = conversationService.createDirectConversation(owner.getId(),
                new CreateDirectConversationRequest(recipient.getId()));
        messageService.sendMessage(owner.getId(), conversation.id(),
                new SendMessageRequest(UUID.randomUUID(), "file", List.of(ready.id())));

        assertThat(new String(attachmentService.downloadContent(recipient.getId(), ready.id()), StandardCharsets.UTF_8))
                .isEqualTo("hello");
        assertThatThrownBy(() -> attachmentService.downloadContent(outsider.getId(), ready.id()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> attachmentService.uploadContent(ready.id(), created.uploadToken(),
                "again".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsContentThatDoesNotMatchDeclaredMimeType() {
        AppUser owner = TestUsers.create(userRepository, "attachment_signature");
        AttachmentResponse created = attachmentService.createUpload(owner.getId(),
                new CreateAttachmentRequest("image.png", "image/png", 8));

        attachmentService.uploadContent(created.id(), created.uploadToken(),
                "not-a-png".getBytes(StandardCharsets.UTF_8));
        assertThat(scanWorker.scan(created.id())).isEqualTo(AttachmentStatus.REJECTED);
        assertThat(attachments.findById(created.id())).get()
                .extracting(Attachment::getStatus).isEqualTo(AttachmentStatus.REJECTED);
        assertThatThrownBy(() -> attachmentService.uploadContent(created.id(), created.uploadToken(),
                "not-a-png".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ForbiddenException.class);
    }
}
