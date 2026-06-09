package com.example.newsfeed.embedding;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class EmbeddingId implements Serializable {
    private String entityType;
    private UUID entityId;
    private String modelVersion;
    public EmbeddingId() {}
    public EmbeddingId(String entityType, UUID entityId, String modelVersion) {
        this.entityType = entityType; this.entityId = entityId; this.modelVersion = modelVersion;
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof EmbeddingId that)) return false;
        return Objects.equals(entityType, that.entityType) && Objects.equals(entityId, that.entityId) && Objects.equals(modelVersion, that.modelVersion);
    }
    @Override public int hashCode() { return Objects.hash(entityType, entityId, modelVersion); }
}
