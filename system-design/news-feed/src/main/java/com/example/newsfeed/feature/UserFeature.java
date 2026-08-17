package com.example.newsfeed.feature;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_features")
public class UserFeature {
    @Id
    private UUID userId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String topicAffinityJson = "{}";
    @Column(nullable = false)
    private double avgSessionSeconds;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String authorAffinityJson = "{}";
    @Column(nullable = false)
    private Instant updatedAt;

    protected UserFeature() {}

    public UserFeature(UUID userId, String topicAffinityJson, double avgSessionSeconds, String authorAffinityJson, Instant updatedAt) {
        this.userId = userId;
        this.topicAffinityJson = topicAffinityJson;
        this.avgSessionSeconds = avgSessionSeconds;
        this.authorAffinityJson = authorAffinityJson;
        this.updatedAt = updatedAt;
    }

    public UUID getUserId() { return userId; }
    public String getTopicAffinityJson() { return topicAffinityJson; }
    public double getAvgSessionSeconds() { return avgSessionSeconds; }
}
