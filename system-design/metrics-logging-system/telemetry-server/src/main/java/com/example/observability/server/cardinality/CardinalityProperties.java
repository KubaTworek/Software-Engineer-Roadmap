package com.example.observability.server.cardinality;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "telemetry.cardinality")
public class CardinalityProperties {
    private int maxLabelsPerSeries = 20;
    private int maxLabelValueLength = 128;
    private int maxSeriesPerMetricPerHour = 50000;
    private int maxValuesPerLabelPerHour = 10000;
    private boolean rejectHighRiskLabels = true;
    private Set<String> blockedLabelKeys = new HashSet<>(Set.of("user_id", "request_id", "session_id", "uuid", "email", "token"));

    public int getMaxLabelsPerSeries() {
        return maxLabelsPerSeries;
    }

    public void setMaxLabelsPerSeries(int maxLabelsPerSeries) {
        this.maxLabelsPerSeries = maxLabelsPerSeries;
    }

    public int getMaxLabelValueLength() {
        return maxLabelValueLength;
    }

    public void setMaxLabelValueLength(int maxLabelValueLength) {
        this.maxLabelValueLength = maxLabelValueLength;
    }

    public int getMaxSeriesPerMetricPerHour() {
        return maxSeriesPerMetricPerHour;
    }

    public void setMaxSeriesPerMetricPerHour(int maxSeriesPerMetricPerHour) {
        this.maxSeriesPerMetricPerHour = maxSeriesPerMetricPerHour;
    }

    public int getMaxValuesPerLabelPerHour() {
        return maxValuesPerLabelPerHour;
    }

    public void setMaxValuesPerLabelPerHour(int maxValuesPerLabelPerHour) {
        this.maxValuesPerLabelPerHour = maxValuesPerLabelPerHour;
    }

    public boolean isRejectHighRiskLabels() {
        return rejectHighRiskLabels;
    }

    public void setRejectHighRiskLabels(boolean rejectHighRiskLabels) {
        this.rejectHighRiskLabels = rejectHighRiskLabels;
    }

    public Set<String> getBlockedLabelKeys() {
        return blockedLabelKeys;
    }

    public void setBlockedLabelKeys(Set<String> blockedLabelKeys) {
        this.blockedLabelKeys = blockedLabelKeys;
    }
}
