package com.ridesharing.mvp.payment;

import com.ridesharing.mvp.ride.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lokalny wykonawca sagi. Outbox nadal publikuje trwały ślad dla świata
 * zewnętrznego, natomiast ten listener pozwala uruchomić laboratorium bez
 * osobnego Payment Service.
 */
@Component
@RequiredArgsConstructor
public class RidePaymentCoordinator {
    private final RideService rides;
    private final PaymentService payments;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void authorize(PaymentAuthorizationRequestedEvent event) {
        var ride = rides.paymentContext(event.rideId());
        try {
            var payment = payments.authorize(ride);
            if (payment.getStatus() == PaymentStatus.AUTHORIZED) {
                rides.startMatchingAfterAuthorization(event.rideId());
            } else {
                rides.markFailedBeforeMatching(event.rideId(), "Payment authorization rejected");
            }
        } catch (RuntimeException failure) {
            rides.markFailedBeforeMatching(event.rideId(), "Payment authorization unavailable");
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void capture(PaymentCaptureRequestedEvent event) {
        payments.capture(rides.paymentContext(event.rideId()));
    }
}
