package pl.jakubtworek.durable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Probe audytu: asercja opisuje zaobserwowaną lukę, NIE docelową gwarancję. */
class CancellationAuditProbeTest {
    @Test @Timeout(30)
    void cancellationAfterChargeLeavesExternalEffectUncompensated() throws Exception {
        var charged = new AtomicBoolean();
        var refunds = new AtomicInteger();
        var fulfillmentStarted = new CountDownLatch(1);
        var releaseActivity = new CountDownLatch(1);
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("audit-cancel");
            worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PaymentActivities() {
                public void charge(String key) { charged.set(true); }
                public void release(String key) { refunds.incrementAndGet(); charged.set(false); }
                public void fulfill(String order) {
                    fulfillmentStarted.countDown();
                    try { releaseActivity.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            });
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("audit-cancel").build());
            try {
                WorkflowClient.start(flow::execute, "audit-order");
                flow.approve();
                assertTrue(fulfillmentStarted.await(10, TimeUnit.SECONDS));
                var stub = WorkflowStub.fromTyped(flow);
                stub.cancel();
                assertThrows(WorkflowFailedException.class, () -> stub.getResult(5, TimeUnit.SECONDS, String.class));
                assertTrue(charged.get(), "Obecna luka: charge nadal istnieje po zakończeniu anulowania");
                assertEquals(0, refunds.get());
                System.out.println("AUDIT CONFIRMED: cancelled workflow left charge=true, refunds=0");
            } finally { releaseActivity.countDown(); }
        }
    }
}