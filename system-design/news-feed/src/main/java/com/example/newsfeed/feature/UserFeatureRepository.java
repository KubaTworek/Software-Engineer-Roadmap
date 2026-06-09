package com.example.newsfeed.feature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface UserFeatureRepository extends JpaRepository<UserFeature, UUID> {}
