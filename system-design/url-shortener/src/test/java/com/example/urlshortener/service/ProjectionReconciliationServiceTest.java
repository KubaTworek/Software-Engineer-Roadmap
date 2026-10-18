package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.storage.UrlLookupRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ProjectionReconciliationServiceTest {
    @Test
    void repairsAStaleProjectionButLeavesAnAheadVersionForInvestigation() {
        ShortUrl staleSource = mock(ShortUrl.class);
        ShortUrl aheadSource = mock(ShortUrl.class);
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        UrlLookupFactory factory = mock(UrlLookupFactory.class);
        ShortUrlCacheService cache = mock(ShortUrlCacheService.class);
        when(repository.findAll(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(staleSource, aheadSource)));

        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UrlLookupRecord staleExpected = new UrlLookupRecord("stale", "https://new", UrlStatus.ACTIVE, null, "r1", now, 5);
        UrlLookupRecord aheadExpected = new UrlLookupRecord("ahead", "https://source", UrlStatus.ACTIVE, null, "r1", now, 5);
        when(factory.from(staleSource)).thenReturn(staleExpected);
        when(factory.from(aheadSource)).thenReturn(aheadExpected);
        when(cache.get("stale")).thenReturn(Optional.of(
                new UrlLookupRecord("stale", "https://old", UrlStatus.ACTIVE, null, "r1", now, 4)));
        when(cache.get("ahead")).thenReturn(Optional.of(
                new UrlLookupRecord("ahead", "https://future", UrlStatus.BLOCKED, null, "r2", now, 6)));
        when(cache.put(staleExpected)).thenReturn(true);

        var report = new ProjectionReconciliationService(repository, factory, cache).reconcile(100, true);

        assertThat(report).isEqualTo(new ProjectionReconciliationReport(2, 0, 0, 1, 0, 1, 1));
        verify(cache).put(staleExpected);
    }
}
