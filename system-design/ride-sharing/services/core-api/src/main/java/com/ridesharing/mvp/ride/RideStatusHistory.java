package com.ridesharing.mvp.ride;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ride_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RideStatusHistory {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id", nullable = false)
    private Ride ride;

    @Enumerated(EnumType.STRING)
    private RideStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus newStatus;

    @Column(nullable = false)
    private String actorType;
    private UUID actorId;
    private String reason;
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
