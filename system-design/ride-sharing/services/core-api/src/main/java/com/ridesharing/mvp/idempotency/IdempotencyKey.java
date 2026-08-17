package com.ridesharing.mvp.idempotency;

import com.ridesharing.mvp.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = {"idem_key", "user_id", "endpoint"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyKey {
    @Id
    private UUID id;

    @Column(name = "idem_key", nullable = false)
    private String key;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String requestHash;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    private Instant lockedUntil;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (expiresAt == null) expiresAt = now.plusSeconds(24 * 3600);
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
