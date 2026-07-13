package com.ridesharing.mvp.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Odporny adapter do providera płatności.
 *
 * W aplikacji ride-sharing płatności są krytyczne, ale zewnętrzny provider
 * może chwilowo nie odpowiadać, zwracać timeouty albo błędy sieciowe.
 *
 * Ta klasa opakowuje właściwego providera płatności mechanizmami:
 * - retry,
 * - circuit breaker,
 * - fallback.
 *
 * Dzięki temu PaymentService nie musi znać szczegółów odporności technicznej.
 */
@Component
@Primary
@RequiredArgsConstructor
public class ResilientPaymentProvider implements PaymentProvider {

    /**
     * Właściwy provider płatności.
     *
     * W tym projekcie jest to MockPaymentProvider, czyli implementacja testowa/MVP.
     * Produkcyjnie tutaj byłby adapter np. do Stripe, Adyen, Przelewy24 albo innego PSP.
     *
     * @Primary sprawia, że Spring będzie domyślnie wstrzykiwał ResilientPaymentProvider
     * jako PaymentProvider, a nie surowy MockPaymentProvider.
     */
    private final MockPaymentProvider delegate;

    /**
     * Autoryzuje płatność, czyli zakłada blokadę środków na karcie/metodzie płatności.
     *
     * W ride-sharingu autoryzacja zwykle dzieje się przed rozpoczęciem lub potwierdzeniem przejazdu.
     * Dzięki temu system wie, że pasażer prawdopodobnie będzie w stanie zapłacić za kurs.
     *
     * @Retry(name = "payments") ponawia operację przy chwilowych błędach.
     * @CircuitBreaker(name = "payments") odcina wywołania, gdy provider płatności masowo zawodzi.
     *
     * fallbackAuthorize zostanie użyty, gdy retry/circuit breaker nie pozwolą uzyskać sukcesu.
     */
    @Override
    @Retry(name = "payments")
    @CircuitBreaker(name = "payments", fallbackMethod = "fallbackAuthorize")
    public ProviderPayment authorize(
            String idempotencyKey,
            BigDecimal amount,
            String currency
    ) {
        return delegate.authorize(idempotencyKey, amount, currency);
    }

    /**
     * Capture płatności, czyli finalne pobranie środków.
     *
     * W typowym flow:
     * - authorize robi hold przed kursem,
     * - capture pobiera finalną kwotę po zakończeniu kursu.
     *
     * Capture też jest opakowany w retry i circuit breaker, bo błędy providera
     * nie powinny powodować utraty informacji o należnej płatności.
     */
    @Override
    @Retry(name = "payments")
    @CircuitBreaker(name = "payments", fallbackMethod = "fallbackCapture")
    public ProviderPayment capture(
            String providerPaymentId,
            BigDecimal amount,
            String currency
    ) {
        return delegate.capture(providerPaymentId, amount, currency);
    }

    /**
     * Fallback dla nieudanej autoryzacji.
     *
     * Zwraca ProviderPayment z success=false.
     * Nie udaje, że płatność się udała — to ważne, bo RideService/PaymentService
     * musi móc zareagować na brak autoryzacji.
     *
     * Generowane ID z prefixem auth-fallback pomaga odróżnić techniczny fallback
     * od prawdziwego ID transakcji u providera.
     */
    @SuppressWarnings("unused")
    ProviderPayment fallbackAuthorize(
            String idempotencyKey,
            BigDecimal amount,
            String currency,
            Throwable ex
    ) {
        return new ProviderPayment(
                "auth-fallback-" + UUID.randomUUID(),
                false,
                "Payment authorization fallback: " + ex.getMessage()
        );
    }

    /**
     * Fallback dla nieudanego capture.
     *
     * Zwraca success=false, ale zachowuje providerPaymentId, jeżeli był dostępny.
     * To ułatwia późniejszą rekoncyliację i ponowienie capture dla tej samej autoryzacji.
     *
     * Capture failure nie powinien usuwać informacji o zakończonym przejeździe.
     * Zwykle płatność powinna przejść w stan CAPTURE_FAILED albo CAPTURE_PENDING
     * i zostać obsłużona przez retry job / support / reconciliation.
     */
    @SuppressWarnings("unused")
    ProviderPayment fallbackCapture(
            String providerPaymentId,
            BigDecimal amount,
            String currency,
            Throwable ex
    ) {
        return new ProviderPayment(
                providerPaymentId == null ? "capture-fallback" : providerPaymentId,
                false,
                "Payment capture fallback: " + ex.getMessage()
        );
    }
}