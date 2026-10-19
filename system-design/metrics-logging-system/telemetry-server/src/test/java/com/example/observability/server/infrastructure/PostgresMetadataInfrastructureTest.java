package com.example.observability.server.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Kontrakt silnie spójnego control plane uruchamiany na prawdziwym PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresMetadataInfrastructureTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void hashesCredentialsAndPersistsAlertStateAcrossStoreInstances() throws Exception {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:src/main/resources/db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO tenant_api_keys(key_id, tenant_id, token_hash, name, roles,
                                            status, created_at, expires_at)
                VALUES ('writer-1', 'demo', ?,
                        'writer', ARRAY['writer'], 'active', now(), now() + interval '1 minute')
                """, java.util.HexFormat.of().formatHex(java.security.MessageDigest
                .getInstance("SHA-256").digest("plaintext-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertThat(jdbc.queryForObject("SELECT token_hash FROM tenant_api_keys WHERE key_id='writer-1'",
                String.class)).doesNotContain("plaintext-secret");

        UUID ruleId = UUID.randomUUID();
        jdbc.update("INSERT INTO alert_rules(rule_id, tenant_id, definition_json, updated_at) VALUES (?,'demo','{}',now())",
                ruleId);
        jdbc.update("""
                INSERT INTO alert_states(rule_id, tenant_id, state, condition_since,
                                         last_evaluated_at, observed_value)
                VALUES (?, 'demo', 'PENDING', now(), now(), 12.0)
                """, ruleId);
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "SELECT state FROM alert_states WHERE rule_id=?", String.class, ruleId))
                .isEqualTo("PENDING");

        UUID silenceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO alert_silences(silence_id,tenant_id,rule_id,reason,starts_at,ends_at)
                VALUES (?, 'demo', ?, 'maintenance', now()-interval '1 minute', now()+interval '10 minutes')
                """, silenceId, ruleId);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM alert_silences
                WHERE tenant_id='demo' AND rule_id=? AND starts_at<=now() AND ends_at>now()
                """, Long.class, ruleId)).isEqualTo(1);

        UUID parentRule = UUID.randomUUID();
        jdbc.update("INSERT INTO alert_rules(rule_id,tenant_id,definition_json,updated_at) VALUES (?,'demo','{}',now())",
                parentRule);
        jdbc.update("""
                INSERT INTO alert_states(rule_id,tenant_id,state,last_evaluated_at,observed_value)
                VALUES (?, 'demo', 'FIRING', now(), 1.0)
                """, parentRule);
        assertThat(jdbc.queryForObject("SELECT state='FIRING' FROM alert_states WHERE rule_id=?",
                Boolean.class, parentRule)).isTrue();
    }
}
