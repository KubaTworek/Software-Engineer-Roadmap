package com.example.filestorage.production.backup;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backup_runs")
public class BackupRun {
    @Id
    private UUID id;
    @Column(name = "backup_type", nullable = false)
    private String backupType;
    @Column(nullable = false)
    private String status;
    private String location;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(columnDefinition = "TEXT")
    private String details;

    protected BackupRun() {}

    public BackupRun(String backupType) {
        this.id = UUID.randomUUID();
        this.backupType = backupType;
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    public void finish(String location, String details) {
        this.status = "COMPLETED";
        this.location = location;
        this.details = details;
        this.finishedAt = Instant.now();
    }

    public void fail(String details) {
        this.status = "FAILED";
        this.details = details;
        this.finishedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBackupType() { return backupType; }
    public String getStatus() { return status; }
    public String getLocation() { return location; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getDetails() { return details; }
}
