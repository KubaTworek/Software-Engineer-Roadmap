package com.example.ratelimiter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.ratelimiter.config.ConfigVersionConflictException;
import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.config.RuleConfigurationValidator;
import com.example.ratelimiter.controlplane.JdbcControlPlaneStore;
import com.example.ratelimiter.controlplane.DurableConfigurationCoordinator;
import com.example.ratelimiter.controlplane.RuleReplica;
import com.example.ratelimiter.usage.JdbcUsageOutbox;
import com.example.ratelimiter.usage.JdbcUsageOutbox.UsageReceipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresControlPlaneAndOutboxInfrastructureTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private PGSimpleDataSource dataSource;

    @BeforeEach
    void schema() throws Exception {
        dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        String migration = new String(new ClassPathResource("db/control-plane.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(migration);
            statement.execute("TRUNCATE rate_limiter_usage_outbox, rate_limiter_usage_receipt, "
                    + "rate_limiter_rule_change RESTART IDENTITY CASCADE");
            statement.execute("UPDATE rate_limiter_config_head SET version=0 WHERE singleton_id=1");
        }
    }

    @Test
    void twoDataPlaneReplicasConvergeOnTheSameDurableVersion() {
        RateLimiterProperties properties = new RateLimiterProperties();
        var store = new JdbcControlPlaneStore(dataSource, new ObjectMapper(),
                new RuleConfigurationValidator(properties));
        var rule = rule("payments", 100);

        assertThat(store.publish(rule, 0, "operator-a")).isEqualTo(1);
        assertThatThrownBy(() -> store.publish(rule("stale", 10), 0, "operator-b"))
                .isInstanceOf(ConfigVersionConflictException.class);

        RuleReplica first = new RuleReplica();
        RuleReplica second = new RuleReplica();
        var firstSnapshot = first.synchronize(store);
        var secondSnapshot = second.synchronize(store);
        assertThat(firstSnapshot.version()).isEqualTo(secondSnapshot.version());
        assertThat(firstSnapshot.rules()).extracting(RateLimiterProperties.Rule::getId)
                .containsExactlyElementsOf(secondSnapshot.rules().stream()
                        .map(RateLimiterProperties.Rule::getId).toList());
        assertThat(first.snapshot().version()).isEqualTo(1);
        assertThat(first.snapshot().rules()).extracting(RateLimiterProperties.Rule::getId)
                .containsExactly("payments");
    }

    @Test
    void crashedRelayIsReclaimedAndEventIdPreventsDoubleCharge() {
        JdbcUsageOutbox outbox = new JdbcUsageOutbox(dataSource);
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        UsageReceipt receipt = new UsageReceipt(id, "tenant-a", 5, now, "{\"operation\":\"export\"}");

        assertThat(outbox.record(receipt)).isTrue();
        assertThat(outbox.record(receipt)).isFalse();
        assertThat(outbox.claim("relay-a", 10, now, Duration.ofSeconds(30))).singleElement()
                .satisfies(claim -> assertThat(claim.attempt()).isEqualTo(1));
        assertThat(outbox.claim("relay-b", 10, now.plusSeconds(20), Duration.ofSeconds(30))).isEmpty();
        assertThat(outbox.claim("relay-b", 10, now.plusSeconds(31), Duration.ofSeconds(30))).singleElement()
                .satisfies(claim -> assertThat(claim.attempt()).isEqualTo(2));
        assertThat(outbox.markPublished(id, "relay-a", now.plusSeconds(32))).isFalse();
        assertThat(outbox.markPublished(id, "relay-b", now.plusSeconds(32))).isTrue();
        assertThat(outbox.authoritativeUsage("tenant-a", now.minusSeconds(1), now.plusSeconds(1)))
                .isEqualTo(5);
    }

    @Test
    void durableCoordinatorPublishesTombstoneAndRollbackAsNewVersions() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getRules().add(rule("payments", 100));
        var validator = new RuleConfigurationValidator(properties);
        var store = new JdbcControlPlaneStore(dataSource, new ObjectMapper(), validator);
        var local = new DynamicConfigService(properties, validator);
        var coordinator = new DurableConfigurationCoordinator(
                store, new RuleReplica(), local, new SimpleMeterRegistry());

        coordinator.initialize();
        assertThat(store.currentVersion()).isEqualTo(1);
        coordinator.upsert(rule("payments", 50), 1, "operator");
        assertThat(local.allRules()).singleElement()
                .extracting(RateLimiterProperties.Rule::getCapacity).isEqualTo(50L);

        assertThat(coordinator.disable("payments", 2, "incident-commander")).isTrue();
        assertThat(local.allRules()).isEmpty();

        assertThat(coordinator.rollback(2, 3, "incident-commander")).isEqualTo(4);
        assertThat(local.allRules()).singleElement()
                .extracting(RateLimiterProperties.Rule::getCapacity).isEqualTo(50L);
    }

    @Test
    void poisonUsageEventStopsAfterBoundedAttempts() {
        JdbcUsageOutbox outbox = new JdbcUsageOutbox(dataSource);
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-14T10:00:00Z");
        outbox.record(new UsageReceipt(id, "tenant-a", 1, now, "{}"));
        JdbcUsageOutbox.ClaimedUsage last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            last = outbox.claim("relay", 1, now.plusSeconds(attempt * 31L), Duration.ofSeconds(30)).getFirst();
        }
        assertThat(last.attempt()).isEqualTo(10);
        assertThat(outbox.markDead(id, "relay", "poison payload")).isTrue();
        assertThat(outbox.claim("other", 1, now.plusSeconds(400), Duration.ofSeconds(30))).isEmpty();
    }

    private RateLimiterProperties.Rule rule(String id, long capacity) {
        var rule = new RateLimiterProperties.Rule();
        rule.setId(id);
        rule.setCapacity(capacity);
        rule.setCost(1);
        rule.setRefillTokensPerSecond(10);
        return rule;
    }
}
