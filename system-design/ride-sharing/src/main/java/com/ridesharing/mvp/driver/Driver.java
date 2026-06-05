package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Driver {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverVerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverAvailabilityStatus availabilityStatus;

    private String vehicleMake;
    private String vehicleModel;
    private String plateNumber;
    private String vehicleColor;
    private String vehicleType;

    @Column(nullable = false)
    private BigDecimal rating;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (rating == null) rating = BigDecimal.valueOf(5.00);
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}
