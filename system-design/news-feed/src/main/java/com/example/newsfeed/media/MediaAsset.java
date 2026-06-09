package com.example.newsfeed.media;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;

@Entity @Table(name = "media_assets")
public class MediaAsset {
    @Id private UUID id;
    private UUID ownerId;
    @Column(columnDefinition = "TEXT") private String objectKey;
    private String mediaType;
    private String status;
    @Column(columnDefinition = "TEXT") private String publicUrl;
    private Integer width;
    private Integer height;
    private Integer durationSeconds;
    private Instant createdAt;
    private Instant updatedAt;
    protected MediaAsset() {}
    public MediaAsset(UUID id, UUID ownerId, String objectKey, String mediaType, String status, String publicUrl, Instant createdAt, Instant updatedAt) {
        this.id=id; this.ownerId=ownerId; this.objectKey=objectKey; this.mediaType=mediaType; this.status=status; this.publicUrl=publicUrl; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }
    public UUID getId(){return id;} public String getPublicUrl(){return publicUrl;} public String getStatus(){return status;}
}
