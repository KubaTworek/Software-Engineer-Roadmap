package com.example.newsfeed.lifecycle;

import com.example.newsfeed.auth.AuthTokenRepository;
import com.example.newsfeed.events.ProcessedKafkaEventRepository;
import com.example.newsfeed.feed.JpaFeedInboxRepository;
import com.example.newsfeed.media.MediaAssetRepository;
import com.example.newsfeed.outbox.DomainEventRepository;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.search.SearchIndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

/**
 * Retencja jest procesem wielomagazynowym. Najpierw usuwamy dokument search,
 * następnie source of truth; cache i inbox są projekcjami z krótszym TTL.
 * Backupów nie da się edytować — dane wygasają zgodnie z osobną polityką.
 */
@Service
public class DataRetentionService {
    private final AuthTokenRepository tokens;
    private final JpaFeedInboxRepository inbox;
    private final ProcessedKafkaEventRepository processedEvents;
    private final DomainEventRepository outbox;
    private final MediaAssetRepository media;
    private final PostRepository posts;
    private final SearchIndexService search;
    private final Clock clock;
    private final boolean scheduledEnabled;

    public DataRetentionService(AuthTokenRepository tokens, JpaFeedInboxRepository inbox,
            ProcessedKafkaEventRepository processedEvents, DomainEventRepository outbox,
            MediaAssetRepository media, PostRepository posts, SearchIndexService search, Clock clock,
            @Value("${newsfeed.retention.scheduled-enabled:false}") boolean scheduledEnabled) {
        this.tokens=tokens; this.inbox=inbox; this.processedEvents=processedEvents; this.outbox=outbox;
        this.media=media; this.posts=posts; this.search=search; this.clock=clock;
        this.scheduledEnabled=scheduledEnabled;
    }

    @Scheduled(cron = "${newsfeed.retention.cron:0 30 3 * * *}")
    public void scheduledCleanup() { if (scheduledEnabled) runOnce(); }

    @Transactional
    public RetentionReport runOnce() {
        Instant now = clock.instant();
        long expiredTokens = tokens.deleteByExpiresAtBefore(now);
        long oldInbox = inbox.deleteByCreatedAtBefore(now.minus(Duration.ofDays(90)));
        long markers = processedEvents.deleteByProcessedAtBefore(now.minus(Duration.ofDays(30)));
        long oldOutbox = outbox.deleteProcessedBefore(now.minus(Duration.ofDays(30)));
        long uploads = media.deleteByStatusAndCreatedAtBefore("pending_upload", now.minus(Duration.ofDays(1)));

        var deleted = posts.findDeletedBefore(now.minus(Duration.ofDays(30)), PageRequest.of(0, 200));
        deleted.forEach(post -> search.deletePost(post.getId().toString()));
        posts.deleteAll(deleted);
        return new RetentionReport(expiredTokens, oldInbox, markers, oldOutbox, uploads, deleted.size());
    }
}
