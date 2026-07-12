package com.example.videostreaming.search;

import com.example.videostreaming.catalog.VideoStatus;
import com.example.videostreaming.catalog.VideoVisibility;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SearchDtos {
    private SearchDtos() {}
    public record SearchResult(UUID id, String title, String description, VideoStatus status, VideoVisibility visibility, Instant publishedAt) implements Serializable {}
    public record SearchResponse(String query, List<SearchResult> results) implements Serializable {}
}
