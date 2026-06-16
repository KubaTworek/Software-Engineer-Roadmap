package com.example.observability.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class LogEventDto {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private String level = "INFO";
    private String service = "unknown";
    private String host = "unknown";
    private String traceId = "";
    private String message = "";
    private Map<String, Object> attributes = new HashMap<>();

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
