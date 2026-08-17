package com.example.observability.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public class MetricSampleDto {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private double value;

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
}
