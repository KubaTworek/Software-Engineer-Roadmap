package pl.jakubtworek.durable;

import io.temporal.activity.Activity;
import io.temporal.client.*;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CancellationCompensationTest {
    @Test @Timeout(30)
    void cancellationAfterChargeWaitsForActivityThenRefundsBeforeClosing() throws Exception {
        var charged = new AtomicBoolean();
        var refunds = new AtomicInteger();
        var fulfilling = new CountDownLatch(1);
        try (var env = TestWorkflowEnvironment.newInstance()) {
            var worker = env.newWorker("cancel");
            worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
            worker.registerActivitiesImplementations(new PaymentActivities() {
                public void charge(String key) { charged.set(true); }
                public void release(String key) { refunds.incrementAndGet(); charged.set(false); }
                public void fulfill(String order) {
                    fulfilling.countDown();
                    // Heartbeat dostarcza anulowanie Activity. Kod ignorujący anulowanie
                    // wymaga timeoutu i idempotencji/tombstone u zewnętrznego providera.
                    while (true) {
                        Activity.getExecutionContext().heartbeat("waiting");
                        try { Thread.sleep(25); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    }
                }
            });
            env.start();
            var flow = env.getWorkflowClient().newWorkflowStub(ApprovalWorkflow.class,
                    WorkflowOptions.newBuilder().setTaskQueue("cancel").build());
            WorkflowClient.start(flow::execute, "cancel-order");
            flow.approve();
            assertTrue(fulfilling.await(10, TimeUnit.SECONDS));
            var stub = WorkflowStub.fromTyped(flow);
            stub.cancel();
            var failure = assertThrows(WorkflowFailedException.class,
                    () -> stub.getResult(15, TimeUnit.SECONDS, String.class));
            assertInstanceOf(io.temporal.failure.CanceledFailure.class, failure.getCause());
            assertFalse(charged.get());
            assertEquals(1, refunds.get());
        }
    }
}
