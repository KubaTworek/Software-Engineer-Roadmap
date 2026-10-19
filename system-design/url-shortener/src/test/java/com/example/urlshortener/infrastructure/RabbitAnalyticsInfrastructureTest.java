package com.example.urlshortener.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.urlshortener.analytics.ClickEventRepository;
import com.example.urlshortener.analytics.DailyUrlStatsId;
import com.example.urlshortener.analytics.DailyUrlStatsRepository;
import com.example.urlshortener.queue.ClickMessage;
import com.example.urlshortener.queue.DurableClickPublisher;
import com.example.urlshortener.queue.ClickPublishOutboxRepository;
import com.example.urlshortener.queue.QueueProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"app.queue.enabled=true", "app.queue.durable-publishing=true", "app.projection-outbox.relay-enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RabbitAnalyticsInfrastructureTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:4.1-alpine"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redisTemplate;
    @Autowired RabbitTemplate rabbit;
    @Autowired QueueProperties queues;
    @Autowired ClickEventRepository events;
    @Autowired DailyUrlStatsRepository dailyStats;
    @Autowired DurableClickPublisher durablePublisher;
    @Autowired ClickPublishOutboxRepository clickOutbox;

    @Test
    void duplicateAtLeastOnceDeliveryProducesOneDurableClick() {
        var click = new ClickMessage("rabbit-duplicate-1", "rabbit-url", Instant.now(), "203.0.113.10", "test", null, "PL", "r1");
        rabbit.convertAndSend(queues.clickExchange(), queues.clickRoutingKey(), click);
        rabbit.convertAndSend(queues.clickExchange(), queues.clickRoutingKey(), click);

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(events.countByShortCode("rabbit-url")).isEqualTo(1));
    }

    @Test
    void concurrentDistinctDeliveriesDoNotLoseDailyIncrements() {
        Instant clickedAt = Instant.parse("2026-06-15T12:00:00Z");
        for (int index = 0; index < 20; index++) {
            rabbit.convertAndSend(queues.clickExchange(), queues.clickRoutingKey(),
                    new ClickMessage("parallel-" + index, "parallel-url", clickedAt,
                            "203.0.113." + index, "test", null, "PL", "parallel-request-" + index));
        }

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(events.countByShortCode("parallel-url")).isEqualTo(20);
            assertThat(dailyStats.findById(new DailyUrlStatsId("parallel-url", java.time.LocalDate.of(2026, 6, 15))))
                    .get().extracting(com.example.urlshortener.analytics.DailyUrlStats::getClicks).isEqualTo(20L);
        });
    }

    @Test
    void durableRelayMarksPublishedOnlyAfterBrokerConfirmation() {
        String eventId = "confirmed-publish-1";
        durablePublisher.enqueue(new ClickMessage(eventId, "confirmed-url", Instant.now(),
                null, "test", null, "PL", "confirmed-request"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(clickOutbox.findById(eventId)).get()
                    .extracting(com.example.urlshortener.queue.ClickPublishOutboxMessage::getPublishedAt)
                    .isNotNull();
            assertThat(events.existsByEventId(eventId)).isTrue();
        });
    }

    @Test
    void poisonMessageIsRetriedThenDeadLettered() {
        var poison = new ClickMessage("rabbit-poison-1", null, Instant.now(), null, null, null, null, "r2");
        rabbit.convertAndSend(queues.clickExchange(), queues.clickRoutingKey(), poison);

        AtomicReference<Message> deadLetter = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message received = rabbit.receive(queues.clickDeadLetterQueue());
            if (received != null) deadLetter.set(received);
            return received != null;
        });
        assertThat(deadLetter.get().getMessageProperties().getHeaders()).containsKey("x-death");
    }
}
