package com.example.notification.application;

import com.example.notification.domain.AuditAction;
import com.example.notification.domain.AuditEvent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final Ports.AuditRepository auditRepository;

    public AuditService(Ports.AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void record(String tenantId, String actor, AuditAction action, UUID resourceId, Map<String, Object> metadata) {
        auditRepository.save(AuditEvent.of(tenantId, actor, action, resourceId, metadata));
    }
}
