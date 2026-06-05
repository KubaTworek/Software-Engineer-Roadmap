package com.example.observability.server.model;

import java.util.ArrayList;
import java.util.List;

public class LogIngestRequest {
    private String tenantId;
    private List<LogEventDto> logs = new ArrayList<>();

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public List<LogEventDto> getLogs() { return logs; }
    public void setLogs(List<LogEventDto> logs) { this.logs = logs; }
}
