package com.example.observability.server.model;

import java.util.ArrayList;
import java.util.List;

public class MetricIngestRequest {
    private String tenantId;
    private List<MetricSeriesDto> series = new ArrayList<>();

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public List<MetricSeriesDto> getSeries() {
        return series;
    }

    public void setSeries(List<MetricSeriesDto> series) {
        this.series = series;
    }
}
