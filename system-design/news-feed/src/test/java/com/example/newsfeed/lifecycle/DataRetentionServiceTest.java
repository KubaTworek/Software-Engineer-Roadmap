package com.example.newsfeed.lifecycle;

import com.example.newsfeed.auth.AuthTokenRepository;
import com.example.newsfeed.events.ProcessedKafkaEventRepository;
import com.example.newsfeed.feed.JpaFeedInboxRepository;
import com.example.newsfeed.media.MediaAssetRepository;
import com.example.newsfeed.outbox.DomainEventRepository;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.search.SearchIndexService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataRetentionServiceTest {
    @Test
    void retentionUsesOneInjectedClockAndKeepsBusinessEffectsOutOfReplay() {
        var tokens=mock(AuthTokenRepository.class); var inbox=mock(JpaFeedInboxRepository.class);
        var markers=mock(ProcessedKafkaEventRepository.class); var outbox=mock(DomainEventRepository.class);
        var media=mock(MediaAssetRepository.class); var posts=mock(PostRepository.class);
        var search=mock(SearchIndexService.class);
        when(tokens.deleteByExpiresAtBefore(any())).thenReturn(2L);
        when(inbox.deleteByCreatedAtBefore(any())).thenReturn(3L);
        when(markers.deleteByProcessedAtBefore(any())).thenReturn(4L);
        when(outbox.deleteProcessedBefore(any())).thenReturn(5);
        when(media.deleteByStatusAndCreatedAtBefore(anyString(), any())).thenReturn(6L);
        when(posts.findDeletedBefore(any(), any())).thenReturn(List.of());
        Clock clock=Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        var service=new DataRetentionService(tokens,inbox,markers,outbox,media,posts,search,clock,false);

        RetentionReport report=service.runOnce();

        assertThat(report).isEqualTo(new RetentionReport(2,3,4,5,6,0));
        verify(tokens).deleteByExpiresAtBefore(clock.instant());
        verify(inbox).deleteByCreatedAtBefore(clock.instant().minus(Duration.ofDays(90)));
    }
}
