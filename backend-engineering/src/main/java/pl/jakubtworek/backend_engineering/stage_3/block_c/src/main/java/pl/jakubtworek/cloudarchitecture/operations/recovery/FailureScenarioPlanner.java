package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.util.List;

public class FailureScenarioPlanner {

    public FailureResponse planFor(CloudDependency dependency) {
        return switch (dependency) {
            case REDIS -> new FailureResponse(
                    dependency,
                    ContinuityMode.DEGRADED,
                    false,
                    List.of(
                            "serve product reads from Cloud SQL with bounded concurrency",
                            "fail closed for operations that require Redis idempotency",
                            "move coarse rate limiting to the edge",
                            "rebuild cache after recovery; never restore it as source of truth"),
                    "Redis is rebuildable cache, but this application also uses it for correctness mechanisms");
            case PUB_SUB -> new FailureResponse(
                    dependency,
                    ContinuityMode.DEGRADED,
                    false,
                    List.of(
                            "keep accepting transactions with their database outbox record",
                            "pause relay retries with backoff",
                            "monitor outbox age against the business SLO",
                            "replay idempotently after Pub/Sub recovery"),
                    "The transactional outbox is the durable buffer while the broker is unavailable");
            case CLOUD_SQL -> new FailureResponse(
                    dependency,
                    ContinuityMode.UNAVAILABLE,
                    true,
                    List.of(
                            "fail writes closed and stop outbox workers",
                            "measure replication lag against RPO",
                            "promote the recovery-region database",
                            "switch traffic only after integrity and application smoke checks"),
                    "Cloud SQL is the source of truth; cache and Pub/Sub cannot reconstruct all business state");
            case PRIMARY_REGION -> new FailureResponse(
                    dependency,
                    ContinuityMode.UNAVAILABLE,
                    true,
                    List.of(
                            "declare disaster using an explicit incident commander",
                            "freeze writes or fence the old primary",
                            "recreate stateless infrastructure from IaC in the recovery region",
                            "promote data services, switch traffic and verify critical journeys"),
                    "Regional DR requires failure-domain isolation, not only multiple zonal replicas");
        };
    }
}
