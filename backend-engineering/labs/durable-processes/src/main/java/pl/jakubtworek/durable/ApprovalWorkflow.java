package pl.jakubtworek.durable;

import io.temporal.workflow.*;

@WorkflowInterface
public interface ApprovalWorkflow {
    @WorkflowMethod String execute(String orderId);
    @SignalMethod void approve();
    @QueryMethod String status();
}
