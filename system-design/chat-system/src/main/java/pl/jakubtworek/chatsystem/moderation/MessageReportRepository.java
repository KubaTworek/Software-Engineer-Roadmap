package pl.jakubtworek.chatsystem.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {
    List<MessageReport> findByStatusOrderByCreatedAtAsc(ModerationStatus status);
}
