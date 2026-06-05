package com.example.observability.server.controller;

import com.example.observability.server.model.LogQueryResult;
import com.example.observability.server.model.MetricPoint;
import com.example.observability.server.repository.TelemetryRepository;
import com.example.observability.server.util.Validation;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/query")
public class QueryController {
    private final TelemetryRepository repository;

    public QueryController(TelemetryRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/logs")
    public List<LogQueryResult> queryLogs(@RequestParam(defaultValue = "demo") String tenantId,
                                          @RequestParam(required = false) String service,
                                          @RequestParam(required = false) String level,
                                          @RequestParam(required = false) String contains,
                                          @RequestParam(required = false) Instant start,
                                          @RequestParam(required = false) Instant end,
                                          @RequestParam(required = false) Integer limit) {
        Instant now = Instant.now();
        Instant effectiveEnd = end == null ? now : end;
        Instant effectiveStart = start == null ? effectiveEnd.minusSeconds(3600) : start;
        return repository.queryLogs(tenantId, service, level, contains, effectiveStart, effectiveEnd, limit);
    }

    @GetMapping("/metrics")
    public List<MetricPoint> queryMetrics(@RequestParam(defaultValue = "demo") String tenantId,
                                          @RequestParam String metricName,
                                          @RequestParam(required = false) String service,
                                          @RequestParam(defaultValue = "60") int minutes,
                                          @RequestParam(defaultValue = "60") int stepSeconds) {
        int safeMinutes = Validation.clamp(minutes, 1, 60 * 24 * 30);
        int safeStep = Validation.clamp(stepSeconds, 1, 3600);
        Instant end = Instant.now();
        Instant start = end.minusSeconds(safeMinutes * 60L);
        return repository.queryMetricSeries(tenantId, metricName, service, start, end, safeStep);
    }
}
