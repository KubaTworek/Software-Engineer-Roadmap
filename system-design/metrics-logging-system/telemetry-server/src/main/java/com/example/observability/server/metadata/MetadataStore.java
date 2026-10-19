package com.example.observability.server.metadata;

import com.example.observability.server.alert.AlertRule;
import com.example.observability.server.alert.AlertSilence;
import com.example.observability.server.auth.AuthContext;
import com.example.observability.server.tenant.TenantModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Silnie spójne repozytorium konfiguracji, kluczy i reguł alertowych. */
@Repository
public class MetadataStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MetadataStore(@Qualifier("metadataJdbc") JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void saveTenant(TenantModels.Tenant tenant) {
        jdbc.update("""
                INSERT INTO tenants(tenant_id, display_name, status, plan, primary_region,
                                    retention_days, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                  display_name=EXCLUDED.display_name, status=EXCLUDED.status,
                  plan=EXCLUDED.plan, primary_region=EXCLUDED.primary_region,
                  retention_days=EXCLUDED.retention_days, updated_at=EXCLUDED.updated_at,
                  version=tenants.version + 1
                """, tenant.tenantId(), tenant.displayName(), tenant.status(), tenant.plan(),
                tenant.primaryRegion(), tenant.retentionDays(), Timestamp.from(tenant.createdAt()),
                Timestamp.from(tenant.updatedAt()));
    }

    public TenantModels.Tenant tenant(String tenantId) {
        return jdbc.query("""
                SELECT tenant_id, display_name, status, plan, primary_region,
                       retention_days, created_at, updated_at
                FROM tenants WHERE tenant_id=?
                """, (rs, row) -> mapTenant(rs), tenantId).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("tenant not found: " + tenantId));
    }

    public List<TenantModels.Tenant> tenants() {
        return jdbc.query("""
                SELECT tenant_id, display_name, status, plan, primary_region,
                       retention_days, created_at, updated_at
                FROM tenants ORDER BY tenant_id LIMIT 1000
                """, (rs, row) -> mapTenant(rs));
    }

    private TenantModels.Tenant mapTenant(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TenantModels.Tenant(rs.getString("tenant_id"), rs.getString("display_name"),
                rs.getString("status"), rs.getString("plan"), rs.getString("primary_region"),
                rs.getInt("retention_days"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    public void saveApiKey(String tenantId, String keyId, String token, String name,
                           Set<String> roles, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO tenant_api_keys(key_id, tenant_id, token_hash, name, roles,
                                            status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, 'active', ?, ?)
                """, keyId, tenantId, sha256(token), name, roles.toArray(String[]::new),
                Timestamp.from(Instant.now()), expiresAt == null ? null : Timestamp.from(expiresAt));
    }

    public Optional<AuthContext> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return jdbc.query("""
                SELECT tenant_id, name, roles FROM tenant_api_keys
                WHERE token_hash=? AND status='active'
                  AND (expires_at IS NULL OR expires_at > now())
                """, (rs, row) -> new AuthContext(rs.getString("tenant_id"), rs.getString("name"),
                sqlArray(rs.getArray("roles"))), sha256(token)).stream().findFirst();
    }

    public List<TenantModels.ApiKeyView> apiKeys(String tenantId) {
        return jdbc.query("""
                SELECT tenant_id, key_id, name, roles, status, created_at, expires_at
                FROM tenant_api_keys WHERE tenant_id=? ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> new TenantModels.ApiKeyView(rs.getString("tenant_id"),
                rs.getString("key_id"), rs.getString("name"), sqlArray(rs.getArray("roles")),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()), tenantId);
    }

    public void saveRule(AlertRule rule) {
        try {
            jdbc.update("""
                    INSERT INTO alert_rules(rule_id, tenant_id, definition_json, updated_at)
                    VALUES (?::uuid, ?, ?, now())
                    ON CONFLICT (rule_id) DO UPDATE SET
                      definition_json=EXCLUDED.definition_json, updated_at=EXCLUDED.updated_at,
                      version=alert_rules.version + 1
                    WHERE alert_rules.tenant_id=EXCLUDED.tenant_id
                    """, rule.getId(), rule.getTenantId(), json.writeValueAsString(rule));
        } catch (Exception exception) {
            throw new IllegalStateException("could not save alert rule", exception);
        }
    }

    public Optional<AlertRule> rule(String id) {
        return jdbc.query("SELECT definition_json FROM alert_rules WHERE rule_id=?::uuid",
                (rs, row) -> readRule(rs.getString(1)), id).stream().findFirst();
    }

    public List<AlertRule> rules() {
        return jdbc.query("SELECT definition_json FROM alert_rules ORDER BY updated_at",
                (rs, row) -> readRule(rs.getString(1)));
    }

    public boolean deleteRule(String tenantId, String id) {
        return jdbc.update("DELETE FROM alert_rules WHERE tenant_id=? AND rule_id=?::uuid", tenantId, id) == 1;
    }

    public Optional<AlertStateRow> alertState(String ruleId) {
        return jdbc.query("""
                SELECT state, condition_since FROM alert_states WHERE rule_id=?::uuid
                """, (rs, row) -> new AlertStateRow(rs.getString(1),
                rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()), ruleId)
                .stream().findFirst();
    }

    public void saveAlertState(AlertRule rule, String state, Instant conditionSince,
                               Instant evaluatedAt, double observedValue) {
        jdbc.update("""
                INSERT INTO alert_states(rule_id, tenant_id, state, condition_since,
                                         last_evaluated_at, observed_value)
                VALUES (?::uuid, ?, ?, ?, ?, ?)
                ON CONFLICT (rule_id) DO UPDATE SET state=EXCLUDED.state,
                  condition_since=EXCLUDED.condition_since,
                  last_evaluated_at=EXCLUDED.last_evaluated_at,
                  observed_value=EXCLUDED.observed_value,
                  version=alert_states.version + 1
                """, rule.getId(), rule.getTenantId(), state,
                conditionSince == null ? null : Timestamp.from(conditionSince),
                Timestamp.from(evaluatedAt), observedValue);
    }

    public record AlertStateRow(String state, Instant conditionSince) { }

    public void saveSilence(AlertSilence silence) {
        jdbc.update("""
                INSERT INTO alert_silences(silence_id, tenant_id, rule_id, starts_at, ends_at, reason)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?)
                """, silence.id(), silence.tenantId(), silence.ruleId(), Timestamp.from(silence.startsAt()),
                Timestamp.from(silence.endsAt()), silence.reason());
    }

    public List<AlertSilence> silences(String tenantId) {
        return jdbc.query("""
                SELECT silence_id::text, tenant_id, rule_id::text, starts_at, ends_at, reason
                FROM alert_silences WHERE tenant_id=? ORDER BY starts_at DESC
                """, (rs, row) -> new AlertSilence(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant(), rs.getString(6)), tenantId);
    }

    public boolean isSilenced(String tenantId, String ruleId, Instant at) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM alert_silences
                WHERE tenant_id=? AND (rule_id IS NULL OR rule_id=?::uuid)
                  AND starts_at <= ? AND ends_at > ?
                """, Long.class, tenantId, ruleId, Timestamp.from(at), Timestamp.from(at));
        return count != null && count > 0;
    }

    public boolean anyRuleFiring(Collection<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) return false;
        String placeholders = String.join(",", Collections.nCopies(ruleIds.size(), "?::uuid"));
        Long count = jdbc.queryForObject("SELECT count(*) FROM alert_states WHERE state='FIRING' AND rule_id IN ("
                + placeholders + ")", Long.class, ruleIds.toArray());
        return count != null && count > 0;
    }

    public void enqueueNotification(String tenantId, String ruleId, String channel,
                                    String target, String payload) {
        jdbc.update("""
                INSERT INTO notification_outbox(delivery_id, tenant_id, rule_id, channel_type,
                    target, payload_json, status, next_attempt_at, created_at)
                VALUES (?::uuid, ?, ?::uuid, ?, ?, ?, 'pending', now(), now())
                """, UUID.randomUUID().toString(), tenantId, ruleId, channel, target, payload);
    }

    public List<NotificationRow> claimNotifications(int limit) {
        return jdbc.query("""
                WITH candidates AS (
                  SELECT delivery_id FROM notification_outbox
                  WHERE status IN ('pending','retry') AND next_attempt_at <= now()
                  ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE notification_outbox n SET status='sending', attempts=attempts+1
                FROM candidates c WHERE n.delivery_id=c.delivery_id
                RETURNING n.delivery_id::text, n.channel_type, n.target,
                          n.payload_json, n.attempts
                """, (rs, row) -> new NotificationRow(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getInt(5)), limit);
    }

    public void notificationDelivered(String deliveryId) {
        jdbc.update("UPDATE notification_outbox SET status='delivered', delivered_at=now() WHERE delivery_id=?::uuid",
                deliveryId);
    }

    public void notificationFailed(String deliveryId, int attempts, String error) {
        long delay = Math.min(3600, 1L << Math.min(12, attempts));
        jdbc.update("""
                UPDATE notification_outbox SET status=CASE WHEN attempts >= 12 THEN 'dead' ELSE 'retry' END,
                  next_attempt_at=now() + (? * interval '1 second'), last_error=?
                WHERE delivery_id=?::uuid
                """, delay, error == null ? "unknown" : error.substring(0, Math.min(1000, error.length())), deliveryId);
    }

    public record NotificationRow(String id, String channel, String target,
                                  String payload, int attempts) { }

    private AlertRule readRule(String value) {
        try { return json.readValue(value, AlertRule.class); }
        catch (Exception exception) { throw new IllegalStateException("invalid alert rule JSON", exception); }
    }

    private Set<String> sqlArray(Array array) throws java.sql.SQLException {
        return array == null ? Set.of() : new HashSet<>(Arrays.asList((String[]) array.getArray()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
