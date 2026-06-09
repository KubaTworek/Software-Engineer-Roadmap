package com.example.newsfeed.feature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
public interface PostFeatureRepository extends JpaRepository<PostFeature, UUID> {
    List<PostFeature> findByPostIdIn(Collection<UUID> postIds);
}
