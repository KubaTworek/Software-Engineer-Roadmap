package com.example.ratelimiter.controlplane;

import com.example.ratelimiter.config.ConfigVersionConflictException;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.config.RuleConfigurationValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * PostgreSQL reference implementation of the control-plane log. `FOR UPDATE`
 * serializes publishers, while expectedVersion prevents a lost operator update.
 * It is intentionally constructed by infrastructure configuration, not enabled
 * in the fast local profile.
 */
public class JdbcControlPlaneStore implements ControlPlaneStore {
    private final DataSource dataSource;
    private final ObjectMapper json;
    private final RuleConfigurationValidator validator;

    public JdbcControlPlaneStore(DataSource dataSource, ObjectMapper json, RuleConfigurationValidator validator) {
        this.dataSource = dataSource;
        this.json = json;
        this.validator = validator;
    }

    @Override
    public long publish(RateLimiterProperties.Rule rule, long expectedVersion, String actor) {
        validator.validate(rule);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long current;
                try (var statement = connection.prepareStatement(
                        "SELECT version FROM rate_limiter_config_head WHERE singleton_id=1 FOR UPDATE");
                     var result = statement.executeQuery()) {
                    result.next();
                    current = result.getLong(1);
                }
                if (current != expectedVersion) throw new ConfigVersionConflictException(expectedVersion, current);
                long next = current + 1;
                try (var statement = connection.prepareStatement("""
                        INSERT INTO rate_limiter_rule_change(version, rule_id, rule_json, actor)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, next);
                    statement.setString(2, rule.getId());
                    statement.setString(3, json.writeValueAsString(rule));
                    statement.setString(4, actor == null || actor.isBlank() ? "unknown" : actor);
                    statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement(
                        "UPDATE rate_limiter_config_head SET version=? WHERE singleton_id=1")) {
                    statement.setLong(1, next);
                    statement.executeUpdate();
                }
                connection.commit();
                return next;
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Cannot publish rule change", exception);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Control-plane database unavailable", exception);
        }
    }

    @Override
    public List<RuleChange> changesAfter(long version) {
        List<RuleChange> changes = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT version, rule_json, actor, occurred_at
                     FROM rate_limiter_rule_change WHERE version > ? ORDER BY version
                     """)) {
            statement.setLong(1, version);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    changes.add(new RuleChange(result.getLong(1),
                            json.readValue(result.getString(2), RateLimiterProperties.Rule.class),
                            result.getString(3), result.getTimestamp(4).toInstant()));
                }
            }
            return List.copyOf(changes);
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read control-plane log", exception);
        }
    }

    @Override
    public long currentVersion() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT version FROM rate_limiter_config_head WHERE singleton_id=1");
             var result = statement.executeQuery()) {
            if (!result.next()) throw new IllegalStateException("Missing control-plane head row");
            return result.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read control-plane version", exception);
        }
    }

    /**
     * Rollback is a new append, never history rewriting. Replicas that already
     * observed the bad version converge on the compensating version normally.
     */
    @Override
    public long republishVersion(long sourceVersion, long expectedVersion, String actor) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT rule_json FROM rate_limiter_rule_change WHERE version=?")) {
            statement.setLong(1, sourceVersion);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Unknown configuration version " + sourceVersion);
                RateLimiterProperties.Rule rule = json.readValue(
                        result.getString(1), RateLimiterProperties.Rule.class);
                return publish(rule, expectedVersion, actor);
            }
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot republish configuration version", exception);
        }
    }
}
