package com.example.newsfeed.experiment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "experiment_assignments")
@IdClass(ExperimentAssignmentId.class)
public class ExperimentAssignment {
    @Id
    private String experimentName;
    @Id
    private UUID userId;
    @Column(nullable = false)
    private String variant;
    @Column(nullable = false)
    private Instant assignedAt;

    protected ExperimentAssignment() {
    }

    public ExperimentAssignment(String experimentName, UUID userId, String variant, Instant assignedAt) {
        this.experimentName = experimentName;
        this.userId = userId;
        this.variant = variant;
        this.assignedAt = assignedAt;
    }

    public String getVariant() {
        return variant;
    }
}
