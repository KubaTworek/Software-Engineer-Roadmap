package pl.jakubtworek.chatsystem.media;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttachmentServiceTest {
    @Autowired AttachmentService attachmentService;
    @Autowired UserRepository userRepository;

    @Test
    void createUploadThenUploadContentAndDownloadAsOwner() {
        AppUser owner = TestUsers.create(userRepository, "owner_attachment");

        AttachmentResponse created = attachmentService.createUpload(
                owner.getId(),
                new CreateAttachmentRequest("note.txt", "text/plain", 5)
        );
        assertThat(created.status()).isEqualTo(AttachmentStatus.UPLOAD_PENDING);
        assertThat(created.uploadUrl()).contains("uploadToken=");

        String uploadToken = created.uploadUrl().substring(created.uploadUrl().indexOf("uploadToken=") + "uploadToken=".length());
        AttachmentResponse uploaded = attachmentService.uploadContent(created.id(), uploadToken, "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(uploaded.status()).isEqualTo(AttachmentStatus.UPLOADED);
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
        String uploadToken = created.uploadUrl().substring(created.uploadUrl().indexOf("uploadToken=") + "uploadToken=".length());
        AttachmentResponse uploaded = attachmentService.uploadContent(created.id(), uploadToken, new byte[] {1, 2, 3, 4});

        assertThat(attachmentService.getReadyAttachmentsOwnedBy(owner.getId(), List.of(uploaded.id()))).hasSize(1);
        assertThatThrownBy(() -> attachmentService.getReadyAttachmentsOwnedBy(other.getId(), List.of(uploaded.id())))
                .isInstanceOf(ForbiddenException.class);
    }
}
