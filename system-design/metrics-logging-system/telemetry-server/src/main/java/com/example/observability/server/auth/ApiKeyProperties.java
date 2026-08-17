package com.example.observability.server.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConfigurationProperties(prefix = "telemetry.auth")
public class ApiKeyProperties {
    private boolean enabled = true;
    private List<ApiKeyDefinition> apiKeys = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ApiKeyDefinition> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<ApiKeyDefinition> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public Optional<ApiKeyDefinition> findByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return apiKeys.stream().filter(k -> token.equals(k.getToken())).findFirst();
    }

    public static class ApiKeyDefinition {
        private String name;
        private String token;
        private String tenantId;
        private Set<String> roles = new HashSet<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public Set<String> getRoles() {
            return roles;
        }

        public void setRoles(Set<String> roles) {
            this.roles = roles;
        }
    }
}
