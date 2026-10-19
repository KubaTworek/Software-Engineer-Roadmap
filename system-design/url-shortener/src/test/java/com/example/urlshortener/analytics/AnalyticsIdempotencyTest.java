package com.example.urlshortener.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.queue.ClickMessage;
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
class AnalyticsIdempotencyTest {
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    StringRedisTemplate redisTemplate;

    @Autowired AnalyticsService analyticsService;
    @Autowired ClickEventRepository events;

    @Test
    void duplicateDeliveryDoesNotDuplicateDurableEventOrAggregate() {
        var message = new ClickMessage(
                "duplicate-event-1", "analytics-idempotency", Instant.parse("2026-09-14T00:00:00Z"),
                "203.0.113.7", "test", null, "PL", "request-1");

        analyticsService.processClick(message);
        analyticsService.processClick(message);

        assertThat(events.countByShortCode("analytics-idempotency")).isEqualTo(1);
    }
}
