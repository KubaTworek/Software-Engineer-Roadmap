package pl.jakubtworek.marketplace.payment.infrastructure.temporal;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.payment.application.ReconcileUnknownPaymentUseCase;
import java.util.UUID;

@Component
public class PaymentLookupAdapter implements PaymentLookupActivity {
    private final ReconcileUnknownPaymentUseCase useCase;
    public PaymentLookupAdapter(ReconcileUnknownPaymentUseCase useCase) { this.useCase = useCase; }
    public String reconcile(String orderId, String correlationId) {
        // Wywołanie przez bean zachowuje proxy @Transactional; Workflow nie wykonuje JDBC.
        return useCase.reconcile(UUID.fromString(orderId), UUID.fromString(correlationId)).name();
    }
}
