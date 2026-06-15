package com.example.filestorage.audit;

import com.example.filestorage.sharing.ResourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, String action, ResourceType resourceType, UUID resourceId, String message) {
        auditLogRepository.save(new AuditLog(actorUserId, action, resourceType, resourceId, message));
    }
}
