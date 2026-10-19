package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests;

/**
 * Executes simplified load test scenarios.
 *
 * The goal is not to replace professional load testing tools.
 * The goal is to encode the expected test behavior in application-level code.
 */
public class LoadTestRunner {

    private final TrafficGenerator trafficGenerator;

    public LoadTestRunner(TrafficGenerator trafficGenerator) {
        if (trafficGenerator == null) throw new IllegalArgumentException("trafficGenerator is required");
        this.trafficGenerator = trafficGenerator;
    }

    public void run(LoadTestScenario scenario) throws Exception {
        if (scenario == null) throw new IllegalArgumentException("scenario is required");
        switch (scenario.type()) {
            case BASELINE, LOAD -> runBaseline(scenario);
            case STEP, STRESS -> runStep(scenario);
            case SPIKE -> runSpike(scenario);
            case SOAK -> runSoak(scenario);
            case CACHE_OFF, MISS_RATIO_UP, DEPENDENCY_FAILURE, RETRY_STORM -> runFailureScenario(scenario);
        }
    }

    private void runBaseline(LoadTestScenario scenario) throws Exception {
        trafficGenerator.runAtRps(scenario.targetRps(), scenario.duration().toSeconds());
    }

    private void runStep(LoadTestScenario scenario) throws Exception {
        long totalSeconds = scenario.duration().toSeconds();
        int steps = (int) Math.min(5, totalSeconds);
        long secondsPerStep = totalSeconds / steps;
        long remainder = totalSeconds % steps;

        for (int index = 0; index < steps; index++) {
            double progress = steps == 1 ? 1.0 : (double) index / (steps - 1);
            int rps = (int) Math.round(
                    scenario.startRps() + (scenario.targetRps() - scenario.startRps()) * progress
            );
            long stepDuration = secondsPerStep + (index < remainder ? 1 : 0);
            trafficGenerator.runAtRps(rps, stepDuration);
        }
    }

    private void runSpike(LoadTestScenario scenario) throws Exception {
        long warmupSeconds = Math.max(1, scenario.duration().toSeconds() / 4);
        long spikeSeconds = scenario.duration().toSeconds() - warmupSeconds;

        trafficGenerator.runAtRps(scenario.startRps(), warmupSeconds);
        trafficGenerator.runAtRps(scenario.targetRps(), spikeSeconds);
    }

    private void runSoak(LoadTestScenario scenario) throws Exception {
        trafficGenerator.runAtRps(scenario.targetRps(), scenario.duration().toSeconds());
    }

    private void runFailureScenario(LoadTestScenario scenario) throws Exception {
        trafficGenerator.runAtRps(scenario.targetRps(), scenario.duration().toSeconds());
    }
}
