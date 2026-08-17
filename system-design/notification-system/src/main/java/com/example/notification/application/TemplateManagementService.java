package com.example.notification.application;

import com.example.notification.domain.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TemplateManagementService {
    private final Ports.TemplateRepository templateRepository;
    private final AuditService auditService;

    public TemplateManagementService(Ports.TemplateRepository templateRepository, AuditService auditService) {
        this.templateRepository = templateRepository;
        this.auditService = auditService;
    }

    public NotificationTemplate create(String tenantId, String actor, String key, NotificationType type, Channel channel,
                                       String subject, String body, Set<String> vars) {
        NotificationTemplate template = new NotificationTemplate(UUID.randomUUID(), tenantId, key, type, channel, 1, subject, body, vars, true);
        templateRepository.save(template);
        auditService.record(tenantId, actor, AuditAction.TEMPLATE_CREATED, template.getId(), Map.of("templateKey", key));
        return template;
    }

    public NotificationTemplate update(String tenantId, String actor, UUID id, String subject, String body, Set<String> vars, boolean active) {
        NotificationTemplate template = templateRepository.findById(tenantId, id)
                .orElseThrow(() -> new Exceptions.NotificationValidationException("Template not found: " + id));
        template.update(subject, body, vars, active);
        templateRepository.save(template);
        auditService.record(tenantId, actor, AuditAction.TEMPLATE_UPDATED, template.getId(), Map.of("version", template.getVersion()));
        return template;
    }

    public void delete(String tenantId, String actor, UUID id) {
        templateRepository.delete(tenantId, id);
        auditService.record(tenantId, actor, AuditAction.TEMPLATE_DELETED, id, Map.of());
    }
}
