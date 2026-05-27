package pl.jakubtworek.booking.aop;

public record MeasuredCall(
        String methodName,
        String label,
        long durationNanos
) {
}
