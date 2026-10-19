package pl.jakubtworek.booking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typowana i walidowana konfiguracja cyklu życia rezerwacji.
 *
 * <p>Błędna wartość ma zatrzymać start aplikacji. Ciche podstawienie wartości
 * domyślnej ukrywa błąd operacyjny i uczy konfiguracji działającej fail-open.</p>
 */
@Validated
@ConfigurationProperties(prefix = "booking.reservation")
public record ReservationPolicyProperties(
        @NotNull Duration holdDuration,
        @Min(1) int expirationBatchSize
) {
}
