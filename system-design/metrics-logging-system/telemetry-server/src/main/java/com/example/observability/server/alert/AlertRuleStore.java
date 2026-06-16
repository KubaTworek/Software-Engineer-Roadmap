package com.example.observability.server.alert;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertRuleStore {
    private final ConcurrentHashMap<String, AlertRule> rules = new ConcurrentHashMap<>();

    public AlertRule save(AlertRule rule) {
        if (rule.getId() == null || rule.getId().isBlank()) {
            rule.setId(java.util.UUID.randomUUID().toString());
        }
        rules.put(rule.getId(), rule);
        return rule;
    }

    public List<AlertRule> all() {
        return new ArrayList<>(rules.values());
    }

    public Optional<AlertRule> find(String id) {
        return Optional.ofNullable(rules.get(id));
    }

    public void delete(String id) {
        rules.remove(id);
    }
}
