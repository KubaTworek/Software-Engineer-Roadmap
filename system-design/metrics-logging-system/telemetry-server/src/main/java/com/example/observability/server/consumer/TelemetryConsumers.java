package com.example.observability.server.consumer;

import com.example.observability.server.model.LogIngestRequest;
import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.repository.TelemetryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TelemetryConsumers {
    private static final Logger log = LoggerFactory.getLogger(TelemetryConsumers.class);
    private final ObjectMapper objectMapper;
    private final TelemetryRepository repository;

    public TelemetryConsumers(ObjectMapper objectMapper, TelemetryRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @KafkaListener(topics = "${telemetry.kafka.logs-topic}", groupId = "telemetry-log-writer")
    public void consumeLogs(String payload) throws Exception {
        LogIngestRequest request = objectMapper.readValue(payload, LogIngestRequest.class);
        if (request.getLogs() == null || request.getLogs().isEmpty()) return;
        repository.insertLogs(request);
        log.debug("Inserted {} logs for tenant {}", request.getLogs().size(), request.getTenantId());
    }

    @KafkaListener(topics = "${telemetry.kafka.metrics-topic}", groupId = "telemetry-metric-writer")
    public void consumeMetrics(String payload) throws Exception {
        MetricIngestRequest request = objectMapper.readValue(payload, MetricIngestRequest.class);
        if (request.getSeries() == null || request.getSeries().isEmpty()) return;
        repository.insertMetrics(request);
        log.debug("Inserted metrics for tenant {}", request.getTenantId());
    }
}
