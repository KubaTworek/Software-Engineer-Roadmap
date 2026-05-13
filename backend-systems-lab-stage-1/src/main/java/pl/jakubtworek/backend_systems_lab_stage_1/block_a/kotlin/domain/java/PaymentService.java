package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.domain.java;

// Service class that handles domain logic.
public class PaymentService {

    public String describeResult(PaymentResult result) {
        // Pattern matching with sealed types allows Java to handle
        // different result types in a controlled way.
        if (result instanceof PaymentSuccess success) {
            return "Payment completed: " + success.transactionId();
        }

        if (result instanceof PaymentFailure failure) {
            return "Payment failed: " + failure.reason();
        }

        throw new IllegalStateException("Unknown payment result");
    }
}