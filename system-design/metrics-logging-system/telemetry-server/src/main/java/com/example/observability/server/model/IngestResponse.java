package com.example.observability.server.model;

public record IngestResponse(String status, int accepted, String topic) {
}
