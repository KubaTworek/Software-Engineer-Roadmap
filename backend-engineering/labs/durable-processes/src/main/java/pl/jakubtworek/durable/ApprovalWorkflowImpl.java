package pl.jakubtworek.durable;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.Saga;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.activity.ActivityCancellationType;
import java.time.Duration;

public class ApprovalWorkflowImpl implements ApprovalWorkflow {
    private boolean approved;
    private String state = "WAITING";
    private final PaymentActivities payment = Workflow.newActivityStub(PaymentActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setHeartbeatTimeout(Duration.ofSeconds(2))
                    .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build()).build());

    public String execute(String orderId) {
        // Historia odtwarza timer i Signal. Zwykłe Thread.sleep lub Instant.now łamałyby model.
        if (!Workflow.await(Duration.ofDays(2), () -> approved)) return state = "EXPIRED";
        // Jedna kompensacja: jej błąd ma przerwać wykonanie, nie zostać jedynie zalogowany.
        var saga = new Saga(new Saga.Options.Builder().setContinueWithError(false).build());
        // Rejestracja przed żądaniem zabezpiecza także niepewny wynik Activity.
        // Provider musi umieć zwolnić nieistniejącą operację idempotentnie.
        saga.addCompensation(payment::release, orderId + ":charge");
        try {
            payment.charge(orderId + ":charge");
            int version = Workflow.getVersion("fulfill-v1", Workflow.DEFAULT_VERSION, 1);
            if (version != Workflow.DEFAULT_VERSION) payment.fulfill(orderId);
            return state = "PAID";
        } catch (CanceledFailure cancellation) {
            compensate(saga);
            throw cancellation;
        } catch (ActivityFailure failure) {
            state = "COMPENSATING";
            compensate(saga);
            if (failure.getCause() instanceof CanceledFailure) throw new CanceledFailure("Workflow cancelled after cleanup");
            return state = "COMPENSATED";
        }
    }

    private void compensate(Saga saga) {
        try {
            // Anulowany root scope nie może anulować własnego refundu.
            Workflow.newDetachedCancellationScope(saga::compensate).run();
        } catch (RuntimeException failed) {
            throw io.temporal.failure.ApplicationFailure.newNonRetryableFailure(
                    "Wymagana ręczna kompensacja", "COMPENSATION_FAILED");
        }
    }

    public void approve() { approved = true; }
    public String status() { return state; }
}
