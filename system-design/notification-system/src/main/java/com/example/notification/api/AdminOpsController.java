package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Ograniczony do ADMIN read model operacyjny; nie zastępuje metryk ani audytu. */
@RestController @RequestMapping("/api/v1/admin/ops")
public class AdminOpsController {
    private final Ports.NotificationQueue queue; private final Ports.NotificationJobRepository jobs;
    private final Ports.OutboxRepository outbox; private final Ports.AuditRepository audit;
    public AdminOpsController(Ports.NotificationQueue queue,Ports.NotificationJobRepository jobs,Ports.OutboxRepository outbox,Ports.AuditRepository audit){this.queue=queue;this.jobs=jobs;this.outbox=outbox;this.audit=audit;}
    @GetMapping("/dashboard") public Map<String,Object> dashboard(){String t=TenantContext.getTenantId();return Map.of("tenantId",t,"queueSize",queue.size(),"jobs",jobs.findAll(t).size(),"dlq",jobs.findDeadLetterJobs(t).size(),"outbox",outbox.findAll(t).size(),"auditEvents",audit.findAll(t).size());}
    @GetMapping("/jobs") public List<ApiDtos.NotificationJobResponse> jobs(){return jobs.findAll(TenantContext.getTenantId()).stream().map(ApiDtos.NotificationJobResponse::from).toList();}
    @GetMapping("/dlq") public List<ApiDtos.NotificationJobResponse> dlq(){return jobs.findDeadLetterJobs(TenantContext.getTenantId()).stream().map(ApiDtos.NotificationJobResponse::from).toList();}
    @GetMapping("/outbox") public List<OutboxEvent> outbox(){return outbox.findAll(TenantContext.getTenantId());}
    @GetMapping("/audit") public List<AuditEvent> audit(){return audit.findAll(TenantContext.getTenantId());}
}
