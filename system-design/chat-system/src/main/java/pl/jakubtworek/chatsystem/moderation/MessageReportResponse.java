package pl.jakubtworek.chatsystem.moderation;

import java.time.Instant;
import java.util.UUID;

public record MessageReportResponse(
        UUID id,
        UUID messageId,
        UUID reporterId,
        String reason,
        String details,
        ModerationStatus status,
        Instant createdAt
) {
    public static MessageReportResponse from(MessageReport report) {
        return new MessageReportResponse(
                report.getId(),
                report.getMessage().getId(),
                report.getReporter().getId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
