package com.example.observability.server.model;

import java.util.ArrayList;
import java.util.List;

public class TraceIngestRequest {
    private String tenantId = "demo";
    private List<TraceSpanDto> spans = new ArrayList<>();

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<TraceSpanDto> getSpans() {
        return spans;
    }

    public void setSpans(List<TraceSpanDto> spans) {
        this.spans = spans;
    }
}
