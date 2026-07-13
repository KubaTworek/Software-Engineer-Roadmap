package com.ridesharing.mvp.ride;

import com.ridesharing.mvp.driver.Driver;
import com.ridesharing.mvp.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ride {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private AppUser passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RideStatus status;

    private double pickupLat;
    private double pickupLng;
    private String pickupAddress;
    private double dropoffLat;
    private double dropoffLng;
    private String dropoffAddress;
    private BigDecimal estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private BigDecimal estimatedPrice;
    private BigDecimal finalPrice;
    private String currency;
    private Instant requestedAt;
    private Instant acceptedAt;
    private Instant driverArrivedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancellationReason;

    @Version
    private int version;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (requestedAt == null) requestedAt = now;
        if (currency == null) currency = "PLN";
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
