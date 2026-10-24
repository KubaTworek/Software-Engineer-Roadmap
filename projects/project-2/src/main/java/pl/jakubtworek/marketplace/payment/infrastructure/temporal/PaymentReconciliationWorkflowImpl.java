package pl.jakubtworek.marketplace.payment.infrastructure.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** Trwała kontynuacja PAYMENT_UNKNOWN; nie przejmuje ownershipu istniejącej sagi. */
public class PaymentReconciliationWorkflowImpl implements PaymentReconciliationWorkflow {
    private final PaymentLookupActivity lookup = Workflow.newActivityStub(PaymentLookupActivity.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build()).build());

    public String reconcile(String orderId, String correlationId) {
        for (int attempt = 0; attempt < 12; attempt++) {
            String status = lookup.reconcile(orderId, correlationId);
            if (!status.equals("UNKNOWN")) return status;
            if (attempt < 11) Workflow.sleep(Duration.ofMinutes(1));
        }
        // Nie nazywamy UNKNOWN porażką płatności: operator musi sprawdzić skutek u providera.
        return "MANUAL_REVIEW";
    }
}
