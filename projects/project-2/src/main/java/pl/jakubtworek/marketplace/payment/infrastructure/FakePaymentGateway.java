package pl.jakubtworek.marketplace.payment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.payment.application.PaymentGateway;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

@Component
public class FakePaymentGateway implements PaymentGateway {
    private final boolean acceptReservations;

    public FakePaymentGateway(@Value("${marketplace.payment.fake.accept-reservations:true}") boolean acceptReservations) {
        this.acceptReservations = acceptReservations;
    }

    public FakePaymentGateway() {
        this(true);
    }

    @Override
    public PaymentReservationResult reserve(UUID orderId, Money amount) {
        if (acceptReservations) {
            return new PaymentReservationResult(true, "fake payment accepted");
        }
        return new PaymentReservationResult(false, "fake payment rejected");
    }
}
