package com.example.observability.server.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Jeden skumulowany rozkład; bucketCounts odpowiada kolejno explicitBounds i +Inf. */
public class HistogramPointDto {
    private String eventId;
    private Instant timestamp;
    private long count;
    private double sum;
    private List<Double> explicitBounds = new ArrayList<>();
    private List<Long> bucketCounts = new ArrayList<>();
    private String exemplarTraceId;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
    public double getSum() { return sum; }
    public void setSum(double sum) { this.sum = sum; }
    public List<Double> getExplicitBounds() { return explicitBounds; }
    public void setExplicitBounds(List<Double> explicitBounds) { this.explicitBounds = explicitBounds; }
    public List<Long> getBucketCounts() { return bucketCounts; }
    public void setBucketCounts(List<Long> bucketCounts) { this.bucketCounts = bucketCounts; }
    public String getExemplarTraceId() { return exemplarTraceId; }
    public void setExemplarTraceId(String exemplarTraceId) { this.exemplarTraceId = exemplarTraceId; }
}
