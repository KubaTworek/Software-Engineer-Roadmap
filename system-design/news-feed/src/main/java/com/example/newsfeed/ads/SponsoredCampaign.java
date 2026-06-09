package com.example.newsfeed.ads;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;

@Entity @Table(name = "sponsored_campaigns")
public class SponsoredCampaign {
    @Id private UUID id;
    private String sponsorName;
    private String status;
    @Column(columnDefinition = "TEXT") private String targetTopics;
    private long maxImpressions;
    private long currentImpressions;
    private double bidScore;
    @Column(columnDefinition = "TEXT") private String creativeText;
    private Instant createdAt;
    private Instant updatedAt;
    protected SponsoredCampaign() {}
    public UUID getId(){return id;} public String getSponsorName(){return sponsorName;} public String getCreativeText(){return creativeText;} public double getBidScore(){return bidScore;}
}
