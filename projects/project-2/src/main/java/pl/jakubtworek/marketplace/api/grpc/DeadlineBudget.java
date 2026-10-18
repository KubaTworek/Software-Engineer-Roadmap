package pl.jakubtworek.marketplace.api.grpc;

import java.time.Duration;

/**
 * Model propagacji deadline w gRPC. Każdy hop przekazuje pozostały budżet, a nie
 * rozpoczyna własny pełny timeout. ReserveForCaller pozostawia czas na serializację
 * odpowiedzi i obsługę błędu. Wygaśnięty deadline kończy pracę fail-fast; samo
 * anulowanie klienta musi jeszcze zostać przeniesione do trwającego zapytania DB/HTTP.
 */
public record DeadlineBudget(Duration remaining) {
    public DeadlineBudget forDownstream(Duration reserveForCaller) {
        Duration child = remaining.minus(reserveForCaller);
        if (child.isZero() || child.isNegative()) throw new DeadlineExceededException();
        return new DeadlineBudget(child);
    }

    public static class DeadlineExceededException extends RuntimeException {}
}
