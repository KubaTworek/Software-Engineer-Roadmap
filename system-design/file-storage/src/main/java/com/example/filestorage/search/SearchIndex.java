package com.example.filestorage.search;

import com.example.filestorage.sharing.ResourceType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_index")
public class SearchIndex {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String searchableText;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SearchIndex() {}

    public SearchIndex(ResourceType resourceType, UUID resourceId, UUID ownerId, String name, String contentType, Long sizeBytes) {
        this.id = resourceId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.ownerId = ownerId;
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.searchableText = ((name == null ? "" : name) + " " + (contentType == null ? "" : contentType)).toLowerCase();
        this.updatedAt = Instant.now();
    }

    public void refresh(String name, String contentType, Long sizeBytes) {
        this.name = name;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.searchableText = ((name == null ? "" : name) + " " + (contentType == null ? "" : contentType)).toLowerCase();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public Instant getUpdatedAt() { return updatedAt; }
}
