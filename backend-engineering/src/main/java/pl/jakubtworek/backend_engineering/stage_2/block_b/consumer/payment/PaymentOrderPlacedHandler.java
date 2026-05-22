package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.payment;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.OrderPlaced;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.EventHandler;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.NonRetryableProcessingException;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.RetryableProcessingException;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay.TemporaryPaymentProviderException;

/**
 * Business handler used by Payment Service.
 *
 * It reacts to OrderPlaced and attempts to authorize payment.
 */
public class PaymentOrderPlacedHandler implements EventHandler<OrderPlaced> {

    private final PaymentService paymentService;

    public PaymentOrderPlacedHandler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Handles OrderPlaced by creating or updating a payment attempt.
     *
     * The paymentService should also be idempotent, usually by using orderId
     * as a unique business key.
     */
    @Override
    public void handle(OrderPlaced event) {
        try {
            paymentService.authorizePayment(
                    event.orderId(),
                    event.totalAmount()
            );
        } catch (TemporaryPaymentProviderException exception) {
            throw new RetryableProcessingException(
                    "Payment provider is temporarily unavailable.",
                    exception
            );
        } catch (InvalidPaymentDataException exception) {
            throw new NonRetryableProcessingException(
                    "OrderPlaced contains invalid payment data.",
                    exception
            );
        }
    }
}