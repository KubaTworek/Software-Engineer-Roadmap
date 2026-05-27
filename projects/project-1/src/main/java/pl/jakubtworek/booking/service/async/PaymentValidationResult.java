package pl.jakubtworek.booking.service.async;

public record PaymentValidationResult(boolean approved, String reason) {
    public static PaymentValidationResult approved(String reason) {
        return new PaymentValidationResult(true, reason);
    }

    public static PaymentValidationResult declined(String reason) {
        return new PaymentValidationResult(false, reason);
    }
}
