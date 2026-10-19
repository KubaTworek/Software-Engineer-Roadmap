package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.exception.OwnershipDeniedException;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.repository.UrlModerationAuditRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UrlOwnershipLifecycleTest {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    StringRedisTemplate redisTemplate;

    @Autowired CreateShortUrlUseCase create;
    @Autowired ModerateShortUrlUseCase lifecycle;
    @Autowired ShortUrlRepository urls;
    @Autowired UrlModerationAuditRepository audit;

    @Test
    void onlyOwnerCanMutateAndEveryAcceptedTransitionIsAudited() {
        var created = create.create(new CreateShortUrlRequest(
                "https://example.com/old", "owned-lifecycle", null), 41L);
        var source = urls.findByShortCode(created.shortCode()).orElseThrow();
        long auditBefore = audit.count();

        assertThatThrownBy(() -> lifecycle.updateOwned(
                created.shortCode(), 42L, "https://example.com/attacker", source.getVersion()))
                .isInstanceOf(OwnershipDeniedException.class);

        var updated = lifecycle.updateOwned(
                created.shortCode(), 41L, "https://example.com/new", source.getVersion());
        var deleted = lifecycle.deleteOwned(created.shortCode(), 41L);

        assertThat(updated.longUrl()).isEqualTo("https://example.com/new");
        assertThat(deleted.status()).isEqualTo(UrlStatus.DELETED);
        assertThat(audit.count()).isEqualTo(auditBefore + 2);
    }
}
