package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates whether key implementation and testing guardrails are present.
 *
 * This review is intentionally simple. In a real organization, each area should
 * be connected to concrete dashboards, runbooks, SLOs, and test evidence.
 */
public record ProductionReadinessReview(
        StatelessApiAssessment statelessApiAssessment,
        AutoscalingGuardrails autoscalingGuardrails,
        List<CacheAsideReadPolicy> cachePolicies,
        RedisCacheConfiguration redisConfiguration,
        StampedeProtectionPolicy stampedeProtectionPolicy,
        RateLimitLayering rateLimitLayering,
        List<RemoteCallPolicy> remoteCallPolicies,
        GracefulDegradationPlan gracefulDegradationPlan,
        List<QueueProcessingPolicy> queuePolicies,
        List<LoadTestScenario> executedTests
) {
    public ProductionReadinessReview {
        if (statelessApiAssessment == null) throw new IllegalArgumentException("statelessApiAssessment is required");
        if (autoscalingGuardrails == null) throw new IllegalArgumentException("autoscalingGuardrails is required");
        if (cachePolicies == null) throw new IllegalArgumentException("cachePolicies is required");
        if (redisConfiguration == null) throw new IllegalArgumentException("redisConfiguration is required");
        if (stampedeProtectionPolicy == null) throw new IllegalArgumentException("stampedeProtectionPolicy is required");
        if (rateLimitLayering == null) throw new IllegalArgumentException("rateLimitLayering is required");
        if (remoteCallPolicies == null) throw new IllegalArgumentException("remoteCallPolicies is required");
        if (gracefulDegradationPlan == null) throw new IllegalArgumentException("gracefulDegradationPlan is required");
        if (queuePolicies == null) throw new IllegalArgumentException("queuePolicies is required");
        if (executedTests == null) throw new IllegalArgumentException("executedTests is required");
    }

    /**
     * Produces review findings from the most important scalability and resilience checks.
     */
    public List<ReadinessFinding> findings() {
        List<ReadinessFinding> findings = new ArrayList<>();

        findings.add(new ReadinessFinding(
                "Stateless API",
                statelessApiAssessment.isHorizontallyScalable(),
                statelessApiAssessment.isHorizontallyScalable()
                        ? "No critical local state blocks horizontal scaling."
                        : "Critical state exists in process memory or local ephemeral disk.",
                "Externalize critical state to database, Redis, object storage, or another backing service."
        ));

        findings.add(new ReadinessFinding(
                "Autoscaling signal",
                !autoscalingGuardrails.usesWeakSignalForIoBoundWorkload(),
                autoscalingGuardrails.usesWeakSignalForIoBoundWorkload()
                        ? "IO-bound workload is scaled only by CPU utilization."
                        : "Autoscaling signal is plausible for the workload type.",
                "Use concurrency, pool wait, queue depth, or dependency latency for IO-bound workloads."
        ));

        findings.add(new ReadinessFinding(
                "Autoscaling max replicas",
                !autoscalingGuardrails.canOverloadDatabaseConnections(),
                autoscalingGuardrails.canOverloadDatabaseConnections()
                        ? "Maximum replicas can exceed the database connection budget."
                        : "Maximum replicas stay within the configured database connection budget.",
                "Lower max replicas, lower per-replica pool size, or increase safe database capacity."
        ));

        boolean cacheFreshnessRisk = cachePolicies.stream().anyMatch(CacheAsideReadPolicy::hasFreshnessRisk);
        findings.add(new ReadinessFinding(
                "Cache invalidation",
                !cacheFreshnessRisk,
                cacheFreshnessRisk
                        ? "At least one strictly fresh data class relies only on TTL expiration."
                        : "Cache invalidation modes are consistent with data freshness classes.",
                "Use invalidate-on-write, refresh-on-write, or stronger consistency for strictly fresh data."
        ));

        findings.add(new ReadinessFinding(
                "Redis eviction",
                !redisConfiguration.isRiskyForClassicCache(),
                redisConfiguration.isRiskyForClassicCache()
                        ? "Redis uses noeviction, which is risky for a classic cache."
                        : "Redis eviction policy is compatible with cache degradation.",
                "For typical caches, start with allkeys-lru and consider allkeys-lfu for stable hot keys."
        ));

        findings.add(new ReadinessFinding(
                "Stampede protection",
                stampedeProtectionPolicy.reducesSynchronizedRefreshes(),
                stampedeProtectionPolicy.reducesSynchronizedRefreshes()
                        ? "At least one stampede protection mechanism is configured."
                        : "Hot keys can refresh synchronously across many requests.",
                "Add single-flight, request coalescing, stale-while-revalidate, or TTL jitter."
        ));

        findings.add(new ReadinessFinding(
                "Rate limiting",
                rateLimitLayering.hasSeparatedEdgeAndBusinessProtection(),
                rateLimitLayering.hasSeparatedEdgeAndBusinessProtection()
                        ? "Rate limiting separates edge IP protection from business identity limits."
                        : "Rate limiting does not clearly separate edge and business protection.",
                "Use edge per-IP limits and application or gateway limits per API key, user, or tenant."
        ));

        boolean unsafeRetry = remoteCallPolicies.stream().anyMatch(RemoteCallPolicy::hasUnsafeRetryConfiguration);
        findings.add(new ReadinessFinding(
                "Retry safety",
                !unsafeRetry,
                unsafeRetry
                        ? "At least one remote call retries a non-idempotent operation."
                        : "Retry policies are compatible with idempotency requirements.",
                "Retry only naturally idempotent operations or operations protected by idempotency keys."
        ));

        boolean retryWithoutJitter = remoteCallPolicies.stream().anyMatch(RemoteCallPolicy::canSynchronizeRetries);
        findings.add(new ReadinessFinding(
                "Retry jitter",
                !retryWithoutJitter,
                retryWithoutJitter
                        ? "At least one retry policy has no jitter."
                        : "Retry policies use jitter where retries are enabled.",
                "Add jitter to exponential backoff to avoid synchronized retry waves."
        ));

        boolean unsafeQueue = queuePolicies.stream().anyMatch(QueueProcessingPolicy::isUnsafeQueueDesign);
        findings.add(new ReadinessFinding(
                "Queue safety",
                !unsafeQueue,
                unsafeQueue
                        ? "At least one queue lacks async suitability, DLQ, or idempotent consumers."
                        : "Queue policies include core safety properties.",
                "Use queues only when async processing is acceptable; add DLQ, age alerts, and idempotent consumers."
        ));

        boolean hasFailureTest = executedTests.stream().anyMatch(test -> test.type() == LoadTestType.DEPENDENCY_FAILURE);
        boolean hasStepTest = executedTests.stream().anyMatch(test -> test.type() == LoadTestType.STEP);
        boolean hasRetryStormTest = executedTests.stream().anyMatch(test -> test.type() == LoadTestType.RETRY_STORM);

        findings.add(new ReadinessFinding(
                "Test coverage",
                hasFailureTest && hasStepTest && hasRetryStormTest,
                hasFailureTest && hasStepTest && hasRetryStormTest
                        ? "Critical capacity and resilience tests are represented."
                        : "Step, dependency-failure, or retry-storm tests are missing.",
                "Validate capacity knees, dependency failure behavior, and retry amplification before production."
        ));

        return findings;
    }
}
