package com.example.newsfeed.post;

/** Stan source of truth, niezależny od opóźnionych projekcji feedu i search. */
public enum PostStatus {
    PENDING_REVIEW,
    PUBLISHED,
    HIDDEN,
    DELETED
}
