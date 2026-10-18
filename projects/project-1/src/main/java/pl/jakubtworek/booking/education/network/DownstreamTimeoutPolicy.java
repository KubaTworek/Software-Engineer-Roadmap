package pl.jakubtworek.booking.education.network;

import java.time.Duration;

/**
 * Wykonywalny model budżetu połączenia z zewnętrznym systemem płatności.
 *
 * <p>Connect timeout obejmuje zestawienie TCP/TLS, read timeout oczekiwanie na
 * dane z już zestawionego połączenia, a request deadline cały przypadek użycia.
 * Te wartości nie są zamienne. Każda próba musi mieścić się w budżecie requestu,
 * łącznie z backoffem; inaczej klient może zwrócić timeout, gdy downstream nadal
 * wykonuje kosztowną pracę.</p>
 */
public record DownstreamTimeoutPolicy(
        Duration connectTimeout,
        Duration readTimeout,
        Duration retryBackoff,
        int maxAttempts
) {
    public DownstreamTimeoutPolicy {
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("retryBackoff cannot be negative");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    /** Najgorszy budżet warstwy klienckiej, bez czasu kolejkowania w puli. */
    public Duration worstCaseClientBudget() {
        Duration attempt = connectTimeout.plus(readTimeout);
        return attempt.multipliedBy(maxAttempts)
                .plus(retryBackoff.multipliedBy(maxAttempts - 1L));
    }

    /**
     * Weryfikuje hierarchię timeoutów: klient powinien przerwać przed proxy,
     * a proxy przed zewnętrznym deadline requestu. Pozostawiony margines pozwala
     * odesłać kontrolowany błąd zamiast zerwania socketu przez warstwę wyżej.
     */
    public boolean fitsWithin(Duration proxyTimeout, Duration requestDeadline) {
        requirePositive(proxyTimeout, "proxyTimeout");
        requirePositive(requestDeadline, "requestDeadline");
        Duration clientBudget = worstCaseClientBudget();
        return clientBudget.compareTo(proxyTimeout) < 0
                && proxyTimeout.compareTo(requestDeadline) < 0;
    }

    /**
     * Retry na kilku warstwach mnoży liczbę prób, zamiast ją dodawać. Dwie próby
     * klienta, proxy i mesh mogą dać 2×2×2 = 8 wywołań jednego żądania biznesowego.
     */
    public static long amplifiedAttempts(int... attemptsPerLayer) {
        long result = 1;
        for (int attempts : attemptsPerLayer) {
            if (attempts < 1) {
                throw new IllegalArgumentException("Each layer must execute at least one attempt");
            }
            result = Math.multiplyExact(result, attempts);
        }
        return result;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
