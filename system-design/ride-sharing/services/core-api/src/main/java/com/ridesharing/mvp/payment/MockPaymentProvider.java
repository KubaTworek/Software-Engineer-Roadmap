package com.ridesharing.mvp.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mockowa implementacja providera płatności.
 *
 * W MVP zastępuje zewnętrzny system płatniczy, np. Stripe, Adyen albo Przelewy24.
 * Dzięki temu aplikację można uruchomić lokalnie bez konta u operatora płatności,
 * kluczy API, webhooków i realnych transakcji.
 *
 * Ta klasa nie wykonuje prawdziwych płatności.
 * Zawsze zwraca sukces, więc nadaje się do developmentu, testów i demo flow.
 */
@Component
@ConditionalOnProperty(
        name = "app.payments.provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockPaymentProvider implements PaymentProvider {

    /**
     * Symuluje autoryzację płatności.
     *
     * W prawdziwym systemie authorize oznaczałoby zablokowanie środków
     * na metodzie płatności pasażera przed rozpoczęciem lub potwierdzeniem przejazdu.
     *
     * Tutaj metoda:
     * - ignoruje realną metodę płatności,
     * - nie kontaktuje się z żadnym operatorem,
     * - generuje sztuczne ID autoryzacji,
     * - zwraca success=true.
     *
     * idempotencyKey jest przyjmowany, bo prawdziwy provider powinien go używać
     * do ochrony przed podwójną autoryzacją. Mock go nie wykorzystuje.
     */
    @Override
    public ProviderPayment authorize(
            String idempotencyKey,
            BigDecimal amount,
            String currency
    ) {
        return new ProviderPayment(
                "mock_auth_" + UUID.randomUUID(),
                true,
                "Authorized by mock provider"
        );
    }

    /**
     * Symuluje finalne pobranie środków.
     *
     * W realnym flow capture powinien zostać wykonany po zakończeniu przejazdu,
     * gdy znana jest finalna kwota do zapłaty.
     *
     * Tutaj metoda:
     * - przyjmuje wcześniejsze providerPaymentId,
     * - nie wykonuje żadnej realnej operacji finansowej,
     * - zwraca success=true.
     *
     * Zachowanie providerPaymentId jest ważne, bo PaymentService może później
     * powiązać capture z wcześniejszą autoryzacją.
     */
    @Override
    public ProviderPayment capture(
            String providerPaymentId,
            BigDecimal amount,
            String currency
    ) {
        return new ProviderPayment(
                providerPaymentId,
                true,
                "Captured by mock provider"
        );
    }
}