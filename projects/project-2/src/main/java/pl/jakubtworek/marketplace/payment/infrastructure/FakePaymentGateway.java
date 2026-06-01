package pl.jakubtworek.marketplace.payment.infrastructure;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

@Component
public class FakePaymentGateway implements PaymentGateway {
    @Override
    public PaymentReservationResult reserve(UUID orderId, Money amount) {
        return new PaymentReservationResult(true, "fake payment accepted");
    }
}
