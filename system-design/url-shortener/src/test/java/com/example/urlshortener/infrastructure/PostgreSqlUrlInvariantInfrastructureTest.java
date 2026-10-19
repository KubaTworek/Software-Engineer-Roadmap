package com.example.urlshortener.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.example.urlshortener.service.ShortUrlService;
import com.example.urlshortener.idempotency.IdempotentCreateService;
import com.example.urlshortener.repository.ShortUrlRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.urlshortener.outbox.UrlProjectionOutboxClaims;
import com.example.urlshortener.outbox.UrlProjectionOutboxMessage;
import com.example.urlshortener.outbox.UrlProjectionOutboxRepository;
import com.example.urlshortener.outbox.UrlProjectionOutboxRelay;
import com.example.urlshortener.service.UrlProjectionUpdater;
import com.example.urlshortener.queue.ClickPublishOutboxClaims;
import com.example.urlshortener.queue.ClickPublishOutboxMessage;
import com.example.urlshortener.queue.ClickPublishOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.mock;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"app.queue.enabled=false", "app.projection-outbox.relay-enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgreSqlUrlInvariantInfrastructureTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    StringRedisTemplate redisTemplate;

    @Autowired ShortUrlService service;
    @Autowired IdempotentCreateService idempotency;
    @Autowired ShortUrlRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UrlProjectionOutboxClaims outboxClaims;
    @Autowired UrlProjectionOutboxRepository outboxRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClickPublishOutboxClaims clickOutboxClaims;
    @Autowired ClickPublishOutboxRepository clickOutboxRepository;

    @Test
    void uniqueConstraintAllowsOnlyOneConcurrentOwnerOfCustomAlias() throws Exception {
        String alias = "concurrent-alias";
        var barrier = new CyclicBarrier(2);
        Callable<Object> create = () -> {
            barrier.await();
            try {
                return service.create(new CreateShortUrlRequest("https://example.com/" + Thread.currentThread().threadId(), alias, null));
            } catch (CustomAliasAlreadyExistsException conflict) {
                return conflict;
            }
        };

        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(create, create));
            results = List.of(futures.get(0).get(), futures.get(1).get());
        }

        assertThat(results).filteredOn(CustomAliasAlreadyExistsException.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(result -> !(result instanceof Exception)).hasSize(1);
    }

    @Test
    void concurrentRetriesWithOneIdempotencyKeyReturnOneBusinessResult() throws Exception {
        var barrier = new CyclicBarrier(2);
        var request = new CreateShortUrlRequest("https://example.com/idempotent-concurrent", null, null);
        Callable<Object> command = () -> {
            barrier.await();
            return idempotency.create("infra-concurrent-idempotency", request);
        };
        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(command, command));
            results = List.of(futures.get(0).get(), futures.get(1).get());
        }
        assertThat(results.get(0)).isEqualTo(results.get(1));
    }

    @Test
    void optimisticVersionRejectsOneConcurrentModerationUpdate() throws Exception {
        service.create(new CreateShortUrlRequest("https://example.com/version", "version-race", null));
        var barrier = new CyclicBarrier(2);
        Callable<Object> moderate = () -> {
            try {
                return new TransactionTemplate(transactionManager).execute(status -> {
                    var url = repository.findByShortCode("version-race").orElseThrow();
                    try { barrier.await(); } catch (Exception exception) { throw new IllegalStateException(exception); }
                    url.block("concurrent", Instant.now());
                    repository.saveAndFlush(url);
                    return "committed";
                });
            } catch (ObjectOptimisticLockingFailureException rejected) {
                return rejected;
            }
        };
        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(moderate, moderate));
            results = List.of(futures.get(0).get(), futures.get(1).get());
        }
        assertThat(results).filteredOn(ObjectOptimisticLockingFailureException.class::isInstance).hasSize(1);
    }

    @Test
    void outboxLeasePreventsASecondWorkerFromClaimingTheSameRow() {
        outboxRepository.save(new UrlProjectionOutboxMessage("leased-event", "{}", Instant.now()));
        assertThat(outboxClaims.claim()).extracting(UrlProjectionOutboxMessage::getEventId).contains("leased-event");
        assertThat(outboxClaims.claim()).extracting(UrlProjectionOutboxMessage::getEventId).doesNotContain("leased-event");
    }

    @Test
    void clickOutboxLeasePreventsTwoRelaysFromPublishingTheSameRowConcurrently() {
        clickOutboxRepository.save(new ClickPublishOutboxMessage("leased-click", "{}", Instant.now()));
        assertThat(clickOutboxClaims.claim()).extracting(ClickPublishOutboxMessage::getEventId)
                .contains("leased-click");
        assertThat(clickOutboxClaims.claim()).extracting(ClickPublishOutboxMessage::getEventId)
                .doesNotContain("leased-click");
    }

    @Test
    void poisonProjectionIsDeadLetteredWithoutBlockingTheQueue() {
        outboxRepository.save(new UrlProjectionOutboxMessage("poison-event", "not-json", Instant.now()));
        new UrlProjectionOutboxRelay(mock(UrlProjectionUpdater.class), objectMapper, outboxClaims, 3).relay();
        assertThat(outboxRepository.findById("poison-event").orElseThrow().getDeadLetteredAt()).isNotNull();
    }

    @Test
    void postgresMigrationRemovesIndexesDuplicatingUniqueConstraints() {
        Integer duplicates = jdbc.queryForObject("""
                select count(*) from pg_indexes
                where indexname in ('idx_urls_short_code', 'idx_enterprise_api_keys_key_hash')
                """, Integer.class);
        assertThat(duplicates).isZero();
    }
}
