package com.example.observability.server.phase3;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RegionReplicationService {
    public ReplicationStatus status() {
        return new ReplicationStatus("single-region-dev", List.of("local"), "not-configured", Instant.now(), "Phase 3 extension point for active-passive/active-active replication");
    }

    public record ReplicationStatus(String mode, List<String> regions, String lagStatus, Instant checkedAt,
                                    String note) {
    }
}
