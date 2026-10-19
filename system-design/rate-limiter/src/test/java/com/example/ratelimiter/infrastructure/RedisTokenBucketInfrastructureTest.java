package com.example.ratelimiter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RedisTokenBucketLimiter;
import com.example.ratelimiter.core.RequestContext;
import com.example.ratelimiter.core.RuleDecision;
import com.example.ratelimiter.quota.QuotaService;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "rate-limiter.usage-events.enabled=false"
})
class RedisTokenBucketInfrastructureTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired RedisTokenBucketLimiter limiter;
    @Autowired QuotaService quotas;
    @Autowired StringRedisTemplate redis;

    @Test
    void concurrentConsumersCannotExceedCapacity() throws Exception {
        var rule = rule("infra-concurrency", 50, 1);
        var context = context("one-subject");
        List<Callable<RuleDecision>> calls = new ArrayList<>();
        for (int i = 0; i < 200; i++) calls.add(() -> limiter.consume(rule, context));

        try (var executor = Executors.newFixedThreadPool(32)) {
            long allowed = executor.invokeAll(calls).stream().filter(future -> {
                try { return future.get().allowed(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).count();
            assertThat(allowed).isEqualTo(50);
        }
    }

    @Test
    void weightedCostConsumesExactlyTheConfiguredBudget() {
        var rule = rule("infra-cost", 10, 4);
        assertThat(limiter.consume(rule, context("weighted")).allowed()).isTrue();
        assertThat(limiter.consume(rule, context("weighted")).allowed()).isTrue();
        assertThat(limiter.consume(rule, context("weighted")).allowed()).isFalse();
    }

    @Test
    void quotaIncrementAndRetentionAreOneAtomicRedisOperation() throws Exception {
        String tenant = "quota-concurrent";
        long now = System.currentTimeMillis();
        List<Callable<QuotaService.QuotaSnapshot>> calls = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            calls.add(() -> quotas.recordAcceptedUsage(new RequestContext(
                    "POST", "/api/exports", "203.0.113.1", null,
                    "user", tenant, "FREE", now), 3));
        }

        try (var executor = Executors.newFixedThreadPool(16)) {
            executor.invokeAll(calls).forEach(future -> {
                try { future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }

        assertThat(quotas.getTenantQuota(tenant).get("used")).isEqualTo("300");
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        assertThat(redis.getExpire("quota:" + tenant + ":" + month)).isGreaterThan(2_000_000);
    }

    @Test
    void bucketHasRetentionAndRecreationStartsFromFullCapacity() {
        var rule = rule("infra-ttl", 2, 1);
        var context = context("ttl-user");
        assertThat(limiter.consume(rule, context).allowed()).isTrue();
        String key = redis.keys("rl:*:infra-ttl").stream().findFirst().orElseThrow();
        assertThat(redis.getExpire(key)).isPositive();

        redis.delete(key); // models expiry/eviction: no stale partial state may survive

        assertThat(limiter.consume(rule, context).allowed()).isTrue();
        assertThat(limiter.consume(rule, context).allowed()).isTrue();
        assertThat(limiter.consume(rule, context).allowed()).isFalse();
    }

    private RateLimiterProperties.Rule rule(String id, long capacity, long cost) {
        var rule = new RateLimiterProperties.Rule();
        rule.setId(id); rule.setType(RateLimiterProperties.RuleType.USER); rule.setUserId("user");
        rule.setCapacity(capacity); rule.setCost(cost); rule.setRefillTokensPerSecond(0.0001);
        return rule;
    }

    private RequestContext context(String user) {
        return new RequestContext("GET", "/api/users", "203.0.113.1", null,
                user, "tenant", "FREE", 1);
    }
}
