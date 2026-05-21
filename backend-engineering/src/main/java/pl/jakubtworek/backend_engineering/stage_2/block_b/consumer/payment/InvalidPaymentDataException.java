package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.payment;

/**
 * Permanent failure caused by invalid payment data.
 *
 * Retrying this error is usually useless unless the event or data is corrected.
 */
public class InvalidPaymentDataException extends RuntimeException {

    public InvalidPaymentDataException(String message) {
        super(message);
    }
}