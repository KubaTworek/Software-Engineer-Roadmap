package com.example.observability.server.controller;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import com.example.observability.server.alert.AlertRuleStore;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private final AlertRuleStore store;
    private final TelemetryRepository repository;

    public AlertController(AlertRuleStore store, TelemetryRepository repository) {
        this.store = store;
        this.repository = repository;
    }

    @PostMapping("/rules")
    public AlertRule create(@RequestBody AlertRule rule) {
        return store.save(rule);
    }

    @GetMapping("/rules")
    public List<AlertRule> list() {
        return store.all();
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public List<AlertEvent> events(@RequestParam(defaultValue = "demo") String tenantId,
                                   @RequestParam(required = false) Integer limit) {
        return repository.queryAlertEvents(tenantId, limit);
    }
}
