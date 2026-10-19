package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

/**
 * Arrival model is part of the experiment contract. Open workload controls
 * arrivals independently of latency; closed workload controls concurrent users.
 */
public record WorkloadProfile(Model model, int arrivalRateRps, int concurrentUsers) {

    public WorkloadProfile {
        if (model == null) {
            throw new IllegalArgumentException("model is required");
        }
        if (model == Model.OPEN && (arrivalRateRps <= 0 || concurrentUsers != 0)) {
            throw new IllegalArgumentException("open model requires positive RPS and no concurrent-user limit");
        }
        if (model == Model.CLOSED && (concurrentUsers <= 0 || arrivalRateRps != 0)) {
            throw new IllegalArgumentException("closed model requires positive concurrency and no arrival rate");
        }
    }

    public static WorkloadProfile openAtRps(int rps) {
        return new WorkloadProfile(Model.OPEN, rps, 0);
    }

    public static WorkloadProfile closedWithUsers(int users) {
        return new WorkloadProfile(Model.CLOSED, 0, users);
    }

    public enum Model {
        OPEN,
        CLOSED
    }
}
