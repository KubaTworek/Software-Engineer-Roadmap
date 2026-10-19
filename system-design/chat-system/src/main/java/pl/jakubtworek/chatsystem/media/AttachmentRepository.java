package pl.jakubtworek.chatsystem.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<Attachment> findByIdAndUploadToken(UUID id, String uploadToken);
    List<Attachment> findTop50ByStatusOrderByUploadedAtAsc(AttachmentStatus status);
}
