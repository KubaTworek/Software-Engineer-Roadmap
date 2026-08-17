package com.example.paymentsystem.audit;

import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String actor, String action, String targetType, String targetId, String details) {
        repository.save(new AuditLog(actor, action, targetType, targetId, details));
    }
}
