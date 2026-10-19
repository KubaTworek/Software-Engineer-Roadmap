package com.example.notification.application;

import com.example.notification.domain.AuditAction;
import com.example.notification.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.time.Clock;

@Service
public class AuditService {
    private final Ports.AuditRepository auditRepository;
    private final Clock clock;

    public AuditService(Ports.AuditRepository auditRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public void record(String tenantId, String actor, AuditAction action, UUID resourceId, Map<String, Object> metadata) {
        auditRepository.save(new AuditEvent(UUID.randomUUID(), tenantId, actor == null ? "system" : actor,
                action, resourceId, metadata == null ? Map.of() : Map.copyOf(metadata), clock.instant()));
    }
}
