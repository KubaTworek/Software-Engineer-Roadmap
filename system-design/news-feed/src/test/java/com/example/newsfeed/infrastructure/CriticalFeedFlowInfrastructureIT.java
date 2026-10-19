package com.example.newsfeed.infrastructure;

import com.example.newsfeed.feed.FeedStorage;
import com.example.newsfeed.follow.FollowService;
import com.example.newsfeed.outbox.OutboxWorker;
import com.example.newsfeed.post.*;
import com.example.newsfeed.search.SearchIndexService;
import com.example.newsfeed.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Krytyczny przepływ przecina prawdziwe granice PostgreSQL, Kafka i Redis:
 * zapis domenowy + outbox → broker → idempotentny fan-out → materialized feed.
 */
@SpringBootTest(properties = {
        "newsfeed.search.initialize-index=false",
        "management.tracing.sampling.probability=0",
        "newsfeed.sponsored.enabled=false",
        "newsfeed.workers.outbox.fixed-delay-ms=60000"
})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class CriticalFeedFlowInfrastructureIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired UserRepository users;
    @Autowired FollowService follows;
    @Autowired PostService posts;
    @Autowired OutboxWorker outbox;
    @Autowired FeedStorage feed;
    @MockitoBean SearchIndexService searchIndex;

    @Test
    void committedPostEventuallyAppearsInFollowersFeed() {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        User author = users.save(new User(UUID.randomUUID(), "author@flow.test", "flow-author", "Author",
                "hash", null, at, at));
        User follower = users.save(new User(UUID.randomUUID(), "follower@flow.test", "flow-follower", "Follower",
                "hash", null, at, at));
        follows.follow(follower, author.getId());
        PostResponse created = posts.createPost(author,
                new CreatePostRequest("A committed post", List.of("systems"), PostVisibility.PUBLIC));

        // Relay może bezpiecznie być wywołany kilka razy: claim i consumer inbox
        // chronią efekty przed duplikacją.
        outbox.processDueEvents();
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            outbox.processDueEvents();
            assertThat(feed.getPostIds(follower.getId(), null, null, 20)).contains(created.id());
        });
    }
}
