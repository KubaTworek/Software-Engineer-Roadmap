package com.example.observability.server.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricSeriesDto {
    private String name;
    private String type = "gauge";
    private String temporality = "cumulative";
    private Map<String, String> labels = new HashMap<>();
    private List<MetricSampleDto> samples = new ArrayList<>();
    private List<HistogramPointDto> histograms = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTemporality() { return temporality; }
    public void setTemporality(String temporality) { this.temporality = temporality; }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public List<MetricSampleDto> getSamples() {
        return samples;
    }

    public void setSamples(List<MetricSampleDto> samples) {
        this.samples = samples;
    }

    public List<HistogramPointDto> getHistograms() { return histograms; }
    public void setHistograms(List<HistogramPointDto> histograms) { this.histograms = histograms; }
}
