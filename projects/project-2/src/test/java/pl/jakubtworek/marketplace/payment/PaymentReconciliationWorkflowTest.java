package pl.jakubtworek.marketplace.payment;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.payment.infrastructure.temporal.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PaymentReconciliationWorkflowTest {
    @Test void unknownIsRetriedWithDurableTimerAndNeverBecomesFalseRejection() {
        var attempts = new AtomicInteger();
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("lookup");
            worker.registerWorkflowImplementationTypes(PaymentReconciliationWorkflowImpl.class);
            worker.registerActivitiesImplementations((PaymentLookupActivity) (order, correlation) -> {
                attempts.incrementAndGet(); return "UNKNOWN";
            });
            env.start();
            var workflow = env.getWorkflowClient().newWorkflowStub(PaymentReconciliationWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("lookup").build());
            assertEquals("MANUAL_REVIEW", workflow.reconcile("order", "correlation"));
            assertEquals(12, attempts.get());
        }
    }
    @Test void resolvedPaymentStopsFurtherPolling() {
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("resolved");
            worker.registerWorkflowImplementationTypes(PaymentReconciliationWorkflowImpl.class);
            var attempts = new AtomicInteger();
            worker.registerActivitiesImplementations((PaymentLookupActivity) (order, correlation) ->
                    attempts.incrementAndGet() == 1 ? "UNKNOWN" : "RESERVED");
            env.start();
            var workflow = env.getWorkflowClient().newWorkflowStub(PaymentReconciliationWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("resolved").build());
            assertEquals("RESERVED", workflow.reconcile("order", "correlation"));
            assertEquals(2, attempts.get());
        }
    }
}
