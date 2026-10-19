package com.example.urlshortener.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.enterprise.BulkCreateShortUrlRequest;
import com.example.urlshortener.enterprise.IdempotentBulkCreateService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ScopedIdempotencyTest {
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) StringRedisTemplate redis;
    @Autowired IdempotentCreateService single;
    @Autowired IdempotentBulkCreateService bulk;

    @Test
    void theSameRawKeyBelongsToIndependentActorNamespaces() {
        var request = new CreateShortUrlRequest("https://example.com/scoped", null, null);
        var tenantA = single.create("tenant-a", "same-key", request);
        var tenantB = single.create("tenant-b", "same-key", request);

        assertThat(tenantA.shortCode()).isNotEqualTo(tenantB.shortCode());
        assertThat(single.create("tenant-a", "same-key", request)).isEqualTo(tenantA);
    }

    @Test
    void bulkCommandReplaysOneAtomicResultPerTenant() {
        var request = new BulkCreateShortUrlRequest(List.of(
                new CreateShortUrlRequest("https://example.com/bulk-one", null, null),
                new CreateShortUrlRequest("https://example.com/bulk-two", null, null)));

        var first = bulk.create(101L, "bulk-key", request);
        var replay = bulk.create(101L, "bulk-key", request);
        var anotherTenant = bulk.create(202L, "bulk-key", request);

        assertThat(replay).isEqualTo(first);
        assertThat(anotherTenant.urls()).extracting(CreateShortUrlResponse::shortCode)
                .doesNotContainAnyElementsOf(first.urls().stream().map(item -> item.shortCode()).toList());
    }
}
