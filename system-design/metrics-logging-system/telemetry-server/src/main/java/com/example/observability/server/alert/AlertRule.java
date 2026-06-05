package com.example.observability.server.alert;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AlertRule {
    private String id = UUID.randomUUID().toString();
    private String tenantId;
    private String name;
    private String metricName;
    private Map<String, String> labelFilters = new HashMap<>();
    private String aggregation = "sum";
    private String operator = ">";
    private double threshold;
    private int windowSeconds = 300;
    private boolean enabled = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }
    public Map<String, String> getLabelFilters() { return labelFilters; }
    public void setLabelFilters(Map<String, String> labelFilters) { this.labelFilters = labelFilters; }
    public String getAggregation() { return aggregation; }
    public void setAggregation(String aggregation) { this.aggregation = aggregation; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public int getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
