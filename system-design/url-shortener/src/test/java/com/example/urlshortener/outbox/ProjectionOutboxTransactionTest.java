package com.example.urlshortener.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.storage.UrlLookupRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ProjectionOutboxTransactionTest {
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redisTemplate;
    @Autowired UrlProjectionOutboxService outbox;
    @Autowired UrlProjectionOutboxRepository repository;
    @Autowired TransactionTemplate transactions;

    @Test
    void rollbackRemovesProjectionIntentTogetherWithBusinessTransaction() {
        var record = new UrlLookupRecord("rolled-back", "https://example.com", UrlStatus.ACTIVE, null, "eu", Instant.now(), 1);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            outbox.enqueue(record);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.findTop100BySentAtIsNullOrderByCreatedAtAsc())
                .noneMatch(message -> message.getPayloadJson().contains("rolled-back"));
    }
}
