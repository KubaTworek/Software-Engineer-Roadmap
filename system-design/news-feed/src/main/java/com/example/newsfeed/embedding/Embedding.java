package com.example.newsfeed.embedding;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "embeddings")
@IdClass(EmbeddingId.class)
public class Embedding {
    @Id private String entityType;
    @Id private UUID entityId;
    @Id private String modelVersion;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String vector;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Embedding() {}
    public Embedding(String entityType, UUID entityId, String modelVersion, String vector, Instant updatedAt) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.modelVersion = modelVersion;
        this.vector = vector;
        this.updatedAt = updatedAt;
    }

    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getModelVersion() { return modelVersion; }
    public String getVector() { return vector; }
}
