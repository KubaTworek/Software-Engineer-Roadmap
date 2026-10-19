package pl.jakubtworek.cloudarchitecture.operations;

import pl.jakubtworek.cloudarchitecture.operations.infrastructure.InfrastructureResource;
import pl.jakubtworek.cloudarchitecture.operations.infrastructure.WorkloadIdentityBinding;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical desired state used by drift and least-privilege exercises. */
public final class ReferenceOperationalArchitecture {

    private ReferenceOperationalArchitecture() {
    }

    public static List<InfrastructureResource> desiredInfrastructure() {
        return List.of(
                new InfrastructureResource("cloud_run.orders_api", "cloud-run-service", Map.of(
                        "region", "europe-west1",
                        "serviceAccount", "orders-api",
                        "ingress", "internal-and-cloud-load-balancing")),
                new InfrastructureResource("cloud_sql.orders_primary", "postgresql", Map.of(
                        "region", "europe-west1",
                        "ha", "regional",
                        "pitr", "true")),
                new InfrastructureResource("cloud_sql.orders_dr", "postgresql-replica", Map.of(
                        "region", "europe-central2",
                        "source", "cloud_sql.orders_primary")),
                new InfrastructureResource("pubsub.order_events", "pubsub-topic", Map.of(
                        "retention", "P7D")),
                new InfrastructureResource("redis.product_cache", "redis", Map.of(
                        "region", "europe-west1",
                        "role", "rebuildable")));
    }

    public static List<WorkloadIdentityBinding> leastPrivilegeBindings() {
        return List.of(
                binding("orders-api", "orders-api@project.iam", Set.of(
                        "cloudsql.instances.connect",
                        "secretmanager.versions.access")),
                binding("outbox-relay", "outbox-relay@project.iam", Set.of(
                        "cloudsql.instances.connect",
                        "pubsub.topics.publish")),
                binding("order-worker", "order-worker@project.iam", Set.of(
                        "cloudsql.instances.connect",
                        "pubsub.subscriptions.consume")));
    }

    private static WorkloadIdentityBinding binding(
            String workload, String serviceAccount, Set<String> permissions) {
        return new WorkloadIdentityBinding(workload, serviceAccount, false, permissions, permissions);
    }
}
