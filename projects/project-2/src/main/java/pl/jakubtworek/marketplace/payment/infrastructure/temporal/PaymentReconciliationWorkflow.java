package pl.jakubtworek.marketplace.payment.infrastructure.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PaymentReconciliationWorkflow {
    @WorkflowMethod String reconcile(String orderId, String correlationId);
}
