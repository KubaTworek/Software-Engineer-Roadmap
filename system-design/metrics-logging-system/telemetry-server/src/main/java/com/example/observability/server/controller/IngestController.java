package com.example.observability.server.controller;

import com.example.observability.server.model.IngestResponse;
import com.example.observability.server.model.LogIngestRequest;
import com.example.observability.server.model.MetricIngestRequest;
import com.example.observability.server.util.Validation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String logsTopic;
    private final String metricsTopic;

    public IngestController(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${telemetry.kafka.logs-topic}") String logsTopic,
            @Value("${telemetry.kafka.metrics-topic}") String metricsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.logsTopic = logsTopic;
        this.metricsTopic = metricsTopic;
    }

    @PostMapping("/logs")
    public CompletableFuture<ResponseEntity<IngestResponse>> ingestLogs(@RequestBody LogIngestRequest request) throws JsonProcessingException {
        request.setTenantId(Validation.required(request.getTenantId(), "default"));
        request.getLogs().forEach(log -> {
            if (log.getTimestamp() == null) log.setTimestamp(Instant.now());
            log.setLevel(Validation.required(log.getLevel(), "INFO").toUpperCase());
            log.setService(Validation.required(log.getService(), "unknown"));
            log.setHost(Validation.required(log.getHost(), "unknown"));
        });
        String payload = objectMapper.writeValueAsString(request);
        return kafkaTemplate.send(logsTopic, request.getTenantId(), payload)
                .thenApply(result -> ResponseEntity.accepted().body(new IngestResponse("accepted", request.getLogs().size(), logsTopic)));
    }

    @PostMapping("/metrics")
    public CompletableFuture<ResponseEntity<IngestResponse>> ingestMetrics(@RequestBody MetricIngestRequest request) throws JsonProcessingException {
        request.setTenantId(Validation.required(request.getTenantId(), "default"));
        request.getSeries().forEach(series -> series.getSamples().forEach(sample -> {
            if (sample.getTimestamp() == null) sample.setTimestamp(Instant.now());
        }));
        String payload = objectMapper.writeValueAsString(request);
        int samples = request.getSeries().stream().mapToInt(s -> s.getSamples().size()).sum();
        return kafkaTemplate.send(metricsTopic, request.getTenantId(), payload)
                .thenApply(result -> ResponseEntity.accepted().body(new IngestResponse("accepted", samples, metricsTopic)));
    }
}
