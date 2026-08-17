package com.example.newsfeed.feature;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_features")
public class PostFeature {
    @Id
    private UUID postId;
    private double qualityScore;
    private double spamScore;
    private double ctr1h;
    private double ctr24h;
    private double reportRate;
    private Instant updatedAt;

    protected PostFeature() {}

    public PostFeature(UUID postId, double qualityScore, double spamScore, double ctr1h, double ctr24h, double reportRate, Instant updatedAt) {
        this.postId = postId;
        this.qualityScore = qualityScore;
        this.spamScore = spamScore;
        this.ctr1h = ctr1h;
        this.ctr24h = ctr24h;
        this.reportRate = reportRate;
        this.updatedAt = updatedAt;
    }

    public UUID getPostId() { return postId; }
    public double getQualityScore() { return qualityScore; }
    public double getSpamScore() { return spamScore; }
    public double getCtr24h() { return ctr24h; }
    public double getReportRate() { return reportRate; }
}
