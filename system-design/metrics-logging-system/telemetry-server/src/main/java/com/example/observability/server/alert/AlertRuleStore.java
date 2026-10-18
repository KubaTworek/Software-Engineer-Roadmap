package com.example.observability.server.alert;

import org.springframework.stereotype.Component;
import com.example.observability.server.metadata.MetadataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertRuleStore {
    private final MetadataStore metadata;

    public AlertRuleStore(MetadataStore metadata) {
        this.metadata = metadata;
    }

    public AlertRule save(AlertRule rule) {
        if (rule.getId() == null || rule.getId().isBlank()) {
            rule.setId(java.util.UUID.randomUUID().toString());
        }
        metadata.saveRule(rule);
        return rule;
    }

    public List<AlertRule> all() {
        return metadata.rules();
    }

    public Optional<AlertRule> find(String id) {
        return metadata.rule(id);
    }

    public boolean delete(String tenantId, String id) {
        return metadata.deleteRule(tenantId, id);
    }
}
