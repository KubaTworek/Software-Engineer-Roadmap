package com.example.newsfeed.lifecycle;

public record RetentionReport(long expiredTokens, long oldInboxEntries, long dedupMarkers,
                              long processedOutboxEvents, long abandonedUploads, long deletedPosts) { }
