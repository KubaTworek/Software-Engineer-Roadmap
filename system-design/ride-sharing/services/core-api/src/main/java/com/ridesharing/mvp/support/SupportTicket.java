package com.ridesharing.mvp.support;

import com.ridesharing.mvp.ride.Ride;
import com.ridesharing.mvp.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SupportTicket {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ride_id")
    private Ride ride;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private AppUser reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_admin_id")
    private AppUser assignedAdmin;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportTicketStatus status;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (priority == null) priority = SupportPriority.NORMAL;
        if (status == null) status = SupportTicketStatus.OPEN;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
