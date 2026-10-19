package pl.jakubtworek.cloudarchitecture.operations.recovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureScenarioPlannerTest {

    private final FailureScenarioPlanner planner = new FailureScenarioPlanner();

    @Test
    void redisLossDegradesDifferentlyForCacheAndCorrectnessFeatures() {
        FailureResponse response = planner.planFor(CloudDependency.REDIS);

        assertThat(response.mode()).isEqualTo(ContinuityMode.DEGRADED);
        assertThat(response.durableDataAtRisk()).isFalse();
        assertThat(response.actions()).anyMatch(action -> action.contains("product reads"));
        assertThat(response.actions()).anyMatch(action -> action.contains("fail closed"));
    }

    @Test
    void pubSubLossUsesDatabaseOutboxAsDurableBuffer() {
        FailureResponse response = planner.planFor(CloudDependency.PUB_SUB);

        assertThat(response.mode()).isEqualTo(ContinuityMode.DEGRADED);
        assertThat(response.actions()).anyMatch(action -> action.contains("outbox"));
        assertThat(response.actions()).anyMatch(action -> action.contains("replay idempotently"));
    }

    @Test
    void sourceOfTruthAndRegionLossRequireExplicitFailover() {
        FailureResponse database = planner.planFor(CloudDependency.CLOUD_SQL);
        FailureResponse region = planner.planFor(CloudDependency.PRIMARY_REGION);

        assertThat(database.mode()).isEqualTo(ContinuityMode.UNAVAILABLE);
        assertThat(database.durableDataAtRisk()).isTrue();
        assertThat(region.actions()).anyMatch(action -> action.contains("fence the old primary"));
        assertThat(region.actions()).anyMatch(action -> action.contains("IaC"));
    }
}
