package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation.tests;

/**
 * Executes simplified load test scenarios.
 *
 * The goal is not to replace professional load testing tools.
 * The goal is to encode the expected test behavior in application-level code.
 */
public class LoadTestRunner {

    private final TrafficGenerator trafficGenerator;

    public LoadTestRunner(TrafficGenerator trafficGenerator) {
        this.trafficGenerator = trafficGenerator;
    }

    public void run(LoadTestScenario scenario) throws Exception {
        switch (scenario.type()) {
            case BASELINE -> runBaseline(scenario);
            case STEP -> runStep(scenario);
            case SPIKE -> runSpike(scenario);
            case SOAK -> runSoak(scenario);
            case CACHE_OFF, MISS_RATIO_UP, DEPENDENCY_FAILURE, RETRY_STORM -> runFailureScenario(scenario);
        }
    }

    private void runBaseline(LoadTestScenario scenario) throws Exception {
        trafficGenerator.runAtRps(scenario.targetRps(), scenario.duration().toSeconds());
    }

    private void runStep(LoadTestScenario scenario) throws Exception {
        int steps = 5;
        int increment = Math.max(1, (scenario.targetRps() - scenario.startRps()) / steps);
        long secondsPerStep = Math.max(1, scenario.duration().toSeconds() / steps);

        for (int rps = scenario.startRps(); rps <= scenario.targetRps(); rps += increment) {
            trafficGenerator.runAtRps(rps, secondsPerStep);
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