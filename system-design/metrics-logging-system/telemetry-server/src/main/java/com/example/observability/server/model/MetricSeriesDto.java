package com.example.observability.server.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricSeriesDto {
    private String name;
    private Map<String, String> labels = new HashMap<>();
    private List<MetricSampleDto> samples = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, String> getLabels() { return labels; }
    public void setLabels(Map<String, String> labels) { this.labels = labels; }
    public List<MetricSampleDto> getSamples() { return samples; }
    public void setSamples(List<MetricSampleDto> samples) { this.samples = samples; }
}
