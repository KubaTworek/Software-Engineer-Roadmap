package com.example.urlshortener.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.service.ShortUrlService;
import com.example.urlshortener.service.ShortUrlCacheService;
import com.example.urlshortener.edge.EdgeCacheInvalidator;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.storage.UrlLookupRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"app.queue.enabled=false", "app.projection-outbox.relay-enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CacheRevocationInfrastructureTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired ShortUrlService service;
    @Autowired ShortUrlCacheService cache;
    @MockitoBean EdgeCacheInvalidator edge;

    @Test
    void committedBlockOverwritesCachedActiveProjection() {
        String alias = "revoked-cache-entry";
        service.create(new CreateShortUrlRequest("https://example.com/revoked", alias, null));
        service.resolveLongUrl(alias); // proves the hot cache entry is usable before moderation

        service.block(alias, "phishing");

        assertThatThrownBy(() -> service.resolveLongUrl(alias))
                .isInstanceOf(ShortUrlGoneException.class);
        verify(edge).purge(alias);
    }

    @Test
    void delayedActiveProjectionCannotResurrectANewerTombstone() {
        Instant now = Instant.now();
        UrlLookupRecord blocked = new UrlLookupRecord(
                "reordered-projection", "https://example.com/safe", UrlStatus.BLOCKED,
                null, "primary", now, 8L);
        UrlLookupRecord delayedActive = new UrlLookupRecord(
                "reordered-projection", "https://example.com/old", UrlStatus.ACTIVE,
                null, "primary", now.minusSeconds(10), 7L);

        assertThat(cache.put(blocked)).isTrue();
        assertThat(cache.put(delayedActive)).isTrue();

        assertThat(cache.get("reordered-projection"))
                .get()
                .extracting(UrlLookupRecord::status, UrlLookupRecord::version)
                .containsExactly(UrlStatus.BLOCKED, 8L);
    }
}
