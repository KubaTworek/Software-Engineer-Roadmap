package com.example.newsfeed.experiment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "experiments")
public class Experiment {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private String name;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private int trafficPercentage;
    @Column(nullable = false, columnDefinition = "TEXT") private String variantsJson;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected Experiment() {}
    public Experiment(UUID id, String name, String status, int trafficPercentage, String variantsJson, Instant createdAt, Instant updatedAt) {
        this.id = id; this.name = name; this.status = status; this.trafficPercentage = trafficPercentage; this.variantsJson = variantsJson; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public int getTrafficPercentage() { return trafficPercentage; }
    public String getVariantsJson() { return variantsJson; }
}
