package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

import java.time.Duration;

public record WorkloadPhase(String name, LoadTestType type, WorkloadProfile workload, Duration duration) {

    public WorkloadPhase {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (type == null || workload == null) throw new IllegalArgumentException("type and workload are required");
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }
}
