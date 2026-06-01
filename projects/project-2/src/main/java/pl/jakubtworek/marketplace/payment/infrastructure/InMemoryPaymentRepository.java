package pl.jakubtworek.marketplace.payment.infrastructure;

import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.payment.application.PaymentRepository;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.payment.domain.PaymentId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<PaymentId, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        payments.put(payment.id(), payment);
        return payment;
    }
}
