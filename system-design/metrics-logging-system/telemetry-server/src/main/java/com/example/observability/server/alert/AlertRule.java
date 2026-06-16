package com.example.observability.server.alert;

import java.util.*;

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
    private String severity = "warning";
    private List<AlertRoute> routes = new ArrayList<>();
    private boolean enabled = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public Map<String, String> getLabelFilters() {
        return labelFilters;
    }

    public void setLabelFilters(Map<String, String> labelFilters) {
        this.labelFilters = labelFilters;
    }

    public String getAggregation() {
        return aggregation;
    }

    public void setAggregation(String aggregation) {
        this.aggregation = aggregation;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<AlertRoute> getRoutes() {
        return routes;
    }

    public void setRoutes(List<AlertRoute> routes) {
        this.routes = routes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static class AlertRoute {
        private String type = "log";
        private String target = "default";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }
}
