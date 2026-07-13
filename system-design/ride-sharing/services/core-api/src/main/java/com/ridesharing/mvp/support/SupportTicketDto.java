package com.ridesharing.mvp.support;

import java.time.Instant;
import java.util.UUID;

public record SupportTicketDto(
        UUID id,
        UUID rideId,
        UUID reporterId,
        UUID assignedAdminId,
        String category,
        SupportPriority priority,
        SupportTicketStatus status,
        String title,
        String description,
        String resolution,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt
) {
    public static SupportTicketDto from(SupportTicket ticket) {
        return new SupportTicketDto(
                ticket.getId(),
                ticket.getRide() == null ? null : ticket.getRide().getId(),
                ticket.getReporter() == null ? null : ticket.getReporter().getId(),
                ticket.getAssignedAdmin() == null ? null : ticket.getAssignedAdmin().getId(),
                ticket.getCategory(), ticket.getPriority(), ticket.getStatus(), ticket.getTitle(), ticket.getDescription(),
                ticket.getResolution(), ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getClosedAt());
    }
}
