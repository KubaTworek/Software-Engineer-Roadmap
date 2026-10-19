package com.example.ratelimiter.admin;

import com.example.ratelimiter.config.ConfigVersionConflictException;
import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RequestContext;
import com.example.ratelimiter.core.RuleMatcher;
import com.example.ratelimiter.controlplane.DurableConfigurationCoordinator;
import com.example.ratelimiter.quota.QuotaService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Versioned local control-plane API; authentication is enforced by a filter. */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final DynamicConfigService config;
    private final RuleMatcher matcher;
    private final QuotaService quotas;
    private final ObjectProvider<DurableConfigurationCoordinator> durable;

    public AdminController(DynamicConfigService config, RuleMatcher matcher, QuotaService quotas,
            ObjectProvider<DurableConfigurationCoordinator> durable) {
        this.config = config; this.matcher = matcher; this.quotas = quotas; this.durable = durable;
    }

    @GetMapping("/rules")
    public Map<String, Object> rules() {
        return Map.of("version", config.version(), "rules", config.allRules());
    }

    @PostMapping("/rules")
    public Map<String, Object> upsert(@Valid @RequestBody RateLimiterProperties.Rule rule,
            @RequestHeader("If-Match") long expected,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "admin") String actor) {
        DurableConfigurationCoordinator coordinator = durable.getIfAvailable();
        long version = coordinator == null
                ? config.upsertRule(rule, expected, actor)
                : coordinator.upsert(rule, expected, actor);
        return Map.of("status", "upserted", "version", version, "ruleId", rule.getId());
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> disable(@PathVariable String id, @RequestHeader("If-Match") long expected,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "admin") String actor) {
        DurableConfigurationCoordinator coordinator = durable.getIfAvailable();
        boolean changed = coordinator == null
                ? config.disableRule(id, expected, actor)
                : coordinator.disable(id, expected, actor);
        return Map.of("status", changed ? "disabled" : "not_found", "version", config.version());
    }

    @PostMapping("/rules/rollback/{sourceVersion}")
    public Map<String, Object> rollback(@PathVariable long sourceVersion,
            @RequestHeader("If-Match") long expected,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "admin") String actor) {
        DurableConfigurationCoordinator coordinator = durable.getIfAvailable();
        if (coordinator == null) {
            throw new IllegalStateException("Rollback requires the durable profile");
        }
        long version = coordinator.rollback(sourceVersion, expected, actor);
        return Map.of("status", "rolled_back", "sourceVersion", sourceVersion, "version", version);
    }

    @GetMapping("/audit")
    public Object audit() { return config.audit(); }

    @GetMapping("/debug/effective-rules")
    public Map<String, Object> effectiveRules(@RequestParam(defaultValue = "GET") String method,
            @RequestParam(defaultValue = "/api/users") String path,
            @RequestParam(required = false) String tenantId, @RequestParam(required = false) String userId,
            @RequestParam(required = false) String apiKeyHash, @RequestParam(defaultValue = "FREE") String plan,
            @RequestParam(defaultValue = "127.0.0.1") String clientIp) {
        RequestContext context = new RequestContext(method, path, clientIp, apiKeyHash, userId, tenantId, plan,
                System.currentTimeMillis());
        return Map.of("context", context, "matchedRules", matcher.match(context), "configVersion", config.version());
    }

    @GetMapping("/tenants/{tenantId}/quota")
    public Object quota(@PathVariable String tenantId) { return quotas.getTenantQuota(tenantId); }

    @ExceptionHandler(ConfigVersionConflictException.class)
    @ResponseStatus(HttpStatus.PRECONDITION_FAILED)
    public Map<String, String> stale(ConfigVersionConflictException exception) {
        return Map.of("error", "stale_configuration", "message", exception.getMessage());
    }
}
