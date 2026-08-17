package com.ridesharing.mvp.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findTop100ByStatusOrderByCreatedAtDesc(SupportTicketStatus status);
    List<SupportTicket> findTop100ByOrderByCreatedAtDesc();
    List<SupportTicket> findByReporterIdOrderByCreatedAtDesc(UUID reporterId);
}
