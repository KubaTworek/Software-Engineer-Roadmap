package pl.jakubtworek.durable;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApprovalWorkflowTest {
    @Test @org.junit.jupiter.api.Timeout(30)
    void freshWorkerReconstructsWaitingWorkflowFromServiceHistory() throws Exception {
        var options = io.temporal.testing.TestEnvironmentOptions.newBuilder()
                .setWorkerFactoryOptions(io.temporal.worker.WorkerFactoryOptions.newBuilder().setWorkflowCacheSize(0).build())
                .build();
        try (var env = TestWorkflowEnvironment.newInstance(options)) {
            env.newWorker("restart").registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("restart").build());
            WorkflowClient.start(flow::execute, "restart-order");
            assertEquals("WAITING", flow.status());
            env.getWorkerFactory().suspendPolling();
            // Cache=0: stary worker nie zachowuje stosu. Po zatrzymaniu pollingu nowa
            // fabryka odtwarza pracę z historii; Service i jego zegar testowy nadal żyją.
            var replacement = io.temporal.worker.WorkerFactory.newInstance(env.getWorkflowClient());
            try {
                var worker = replacement.newWorker("restart");
                worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
                worker.registerActivitiesImplementations(new PaymentActivities() {
                    public void charge(String key) { }
                    public void release(String key) { }
                    public void fulfill(String order) { }
                });
                replacement.start();
                flow.approve();
                assertEquals("PAID", io.temporal.client.WorkflowStub.fromTyped(flow)
                        .getResult(15, java.util.concurrent.TimeUnit.SECONDS, String.class));
            } finally {
                replacement.shutdownNow();
                replacement.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
    }

    @Test void retryAfterEffectDoesNotChargeTwiceAndHistoryCanBeReplayed() throws Exception {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("approval");
            worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            var attempts = new AtomicInteger();
            Set<String> ledger = java.util.Collections.synchronizedSet(new HashSet<>());
            worker.registerActivitiesImplementations(new PaymentActivities() {
                public void charge(String key) {
                    ledger.add(key); // Model providera z idempotency key, NIE gwarancja Temporal.
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Efekt wykonany, odpowiedź utracona");
                }
                public void release(String key) { ledger.remove(key); }
                public void fulfill(String order) { }
            });
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("approval").setWorkflowId("order-1").build());
            var execution = WorkflowClient.start(flow::execute, "order-1");
            flow.approve();
            assertEquals("PAID", io.temporal.client.WorkflowStub.fromTyped(flow).getResult(String.class));
            assertEquals(2, attempts.get());
            assertEquals(Set.of("order-1:charge"), ledger);
            WorkflowReplayer.replayWorkflowExecution(env.getWorkflowClient().fetchHistory(execution.getWorkflowId()), ApprovalWorkflowImpl.class);
        }
    }

    @Test void timerExpiresWithoutWaitingTwoRealDays() {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            env.newWorker("expiry").registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("expiry").build());
            assertEquals("EXPIRED", flow.execute("no-approval"));
        }
    }

    @Test void fulfillmentFailureCompensatesPayment() {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("compensate");
            worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            Set<String> ledger = java.util.Collections.synchronizedSet(new HashSet<>());
            worker.registerActivitiesImplementations(new PaymentActivities() {
                public void charge(String key) { ledger.add(key); }
                public void release(String key) { ledger.remove(key); }
                public void fulfill(String order) {
                    throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure("Brak stocku", "STOCK_REJECTED");
                }
            });
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("compensate").build());
            WorkflowClient.start(flow::execute, "compensated-order");
            flow.approve();
            assertEquals("COMPENSATED", io.temporal.client.WorkflowStub.fromTyped(flow).getResult(String.class));
            assertTrue(ledger.isEmpty());
        }
    }

    @Test void compensationFailureIsNotReportedAsSuccessfulCompensation() {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("failed-compensation");
            worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PaymentActivities() {
                public void charge(String key) { }
                public void release(String key) {
                    throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure("Provider niedostępny", "REFUND_FAILED");
                }
                public void fulfill(String order) {
                    throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure("Brak stocku", "STOCK_REJECTED");
                }
            });
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("failed-compensation").build());
            WorkflowClient.start(flow::execute, "manual-refund");
            flow.approve();
            var failure = assertThrows(io.temporal.client.WorkflowFailedException.class,
                    () -> io.temporal.client.WorkflowStub.fromTyped(flow).getResult(String.class));
            assertEquals("COMPENSATION_FAILED",
                    assertInstanceOf(io.temporal.failure.ApplicationFailure.class, failure.getCause()).getType());
        }
    }
}
