package com.example.observability.server.model;

import java.time.Instant;

public record MetricPoint(Instant timestamp, double value) {}
