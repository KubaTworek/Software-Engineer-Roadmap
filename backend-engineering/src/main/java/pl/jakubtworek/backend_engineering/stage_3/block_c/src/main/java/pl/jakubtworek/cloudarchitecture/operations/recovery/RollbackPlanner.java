package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.util.List;

public class RollbackPlanner {

    public RollbackDecision decide(ReleaseChange release) {
        if (release.migrationKind() == MigrationKind.CONTRACT
                || !release.previousApplicationCompatibleWithCurrentSchema()) {
            return new RollbackDecision(
                    false,
                    List.of(
                            "stop rollout and preserve evidence",
                            "deploy a forward-compatible fix or restore data into an isolated database",
                            "never run an automatic down migration over production data"),
                    "The previous application cannot safely use the current schema");
        }

        return new RollbackDecision(
                true,
                List.of(
                        "rollback-image to " + release.previousImageDigest(),
                        "leave the backward-compatible schema in place",
                        "verify health, error rate and critical business journey",
                        "resume traffic gradually"),
                "Expand/backfill migrations keep the previous application compatible during rollout");
    }
}
