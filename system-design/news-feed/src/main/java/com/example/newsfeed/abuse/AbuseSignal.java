package com.example.newsfeed.abuse;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name = "abuse_signals")
public class AbuseSignal {
    @Id private UUID id;
    private UUID userId;
    private String signalType;
    private double score;
    @Column(columnDefinition = "TEXT") private String metadata;
    private Instant createdAt;
    protected AbuseSignal() {}
    public AbuseSignal(UUID id, UUID userId, String signalType, double score, String metadata, Instant createdAt) {
        this.id=id; this.userId=userId; this.signalType=signalType; this.score=score; this.metadata=metadata; this.createdAt=createdAt;
    }
}
