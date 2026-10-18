package com.example.urlshortener.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DurableClickPublisherTest {
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redis;
    @Autowired DurableClickPublisher publisher;
    @Autowired ClickPublishOutboxRepository repository;

    @Test
    void persistsProducerIntentBeforeBrokerDelivery() {
        var message = new ClickMessage("durable-click-1", "code", Instant.now(), null, null, null, null, "request-1");
        publisher.enqueue(message);
        assertThat(repository.findById("durable-click-1")).isPresent();
    }
}
