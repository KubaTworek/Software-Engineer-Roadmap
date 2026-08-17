package com.example.newsfeed.embedding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EmbeddingRepository extends JpaRepository<Embedding, EmbeddingId> {
    Optional<Embedding> findByEntityTypeAndEntityIdAndModelVersion(String entityType, UUID entityId, String modelVersion);
    List<Embedding> findTop200ByEntityTypeAndModelVersion(String entityType, String modelVersion);
}
