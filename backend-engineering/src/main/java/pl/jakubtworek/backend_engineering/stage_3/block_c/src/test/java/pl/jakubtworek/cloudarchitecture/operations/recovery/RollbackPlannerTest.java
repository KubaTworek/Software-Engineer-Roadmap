package pl.jakubtworek.cloudarchitecture.operations.recovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RollbackPlannerTest {

    private final RollbackPlanner planner = new RollbackPlanner();

    @Test
    void rollsApplicationBackWithoutRollingCompatibleSchemaBack() {
        RollbackDecision decision = planner.decide(new ReleaseChange(
                "sha256:new", "sha256:old", "17", MigrationKind.EXPAND, true));

        assertThat(decision.safe()).isTrue();
        assertThat(decision.actions()).contains(
                "rollback-image to sha256:old",
                "leave the backward-compatible schema in place");
    }

    @Test
    void refusesAutomaticRollbackAfterDestructiveContractMigration() {
        RollbackDecision decision = planner.decide(new ReleaseChange(
                "sha256:new", "sha256:old", "18", MigrationKind.CONTRACT, false));

        assertThat(decision.safe()).isFalse();
        assertThat(decision.actions()).anyMatch(action -> action.contains("forward-compatible fix"));
        assertThat(decision.actions()).anyMatch(action -> action.contains("never run an automatic down migration"));
    }
}
