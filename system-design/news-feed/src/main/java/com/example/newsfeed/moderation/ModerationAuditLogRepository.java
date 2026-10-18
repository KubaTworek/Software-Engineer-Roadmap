package com.example.newsfeed.moderation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ModerationAuditLogRepository extends JpaRepository<ModerationAuditLog, UUID> { }
