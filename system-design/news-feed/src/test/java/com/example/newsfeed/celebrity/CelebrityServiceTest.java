package com.example.newsfeed.celebrity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class CelebrityServiceTest {
    @Test
    void hysteresisPreventsStrategyFlappingNearPromotionThreshold() {
        CelebrityAuthorRepository authors = mock(CelebrityAuthorRepository.class);
        CelebrityPostRepository posts = mock(CelebrityPostRepository.class);
        UUID authorId = UUID.randomUUID();
        CelebrityAuthor celebrity = new CelebrityAuthor(authorId, 100_000, Instant.now(), Instant.now());
        when(authors.findById(authorId)).thenReturn(Optional.of(celebrity));
        CelebrityService service = new CelebrityService(authors, posts, 100_000);

        service.refreshCelebrityStatus(authorId, 99_999);

        verify(authors, never()).delete(any());
        verify(posts, never()).deleteByAuthorId(authorId);
    }

    @Test
    void demotionRemovesPullProjectionOnlyBelowLowerThreshold() {
        CelebrityAuthorRepository authors = mock(CelebrityAuthorRepository.class);
        CelebrityPostRepository posts = mock(CelebrityPostRepository.class);
        UUID authorId = UUID.randomUUID();
        CelebrityAuthor celebrity = new CelebrityAuthor(authorId, 100_000, Instant.now(), Instant.now());
        when(authors.findById(authorId)).thenReturn(Optional.of(celebrity));
        CelebrityService service = new CelebrityService(authors, posts, 100_000);

        service.refreshCelebrityStatus(authorId, 79_999);

        verify(authors).delete(celebrity);
        verify(posts).deleteByAuthorId(authorId);
    }
}
