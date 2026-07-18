package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Mockowa implementacja klienta PSP.
 *
 * PSP oznacza Payment Service Provider, czyli zewnętrznego dostawcę płatności,
 * np. Stripe, Adyen, PayU albo innego operatora.
 *
 * Ta klasa symuluje integrację z takim providerem bez wykonywania prawdziwych
 * requestów HTTP i bez pobierania realnych środków od klienta.
 *
 * Dzięki temu możemy testować cały flow Payment Systemu:
 * - routing do providera,
 * - idempotencję,
 * - tworzenie płatności,
 * - zapis providerPaymentId,
 * - obsługę checkoutUrl,
 * - circuit breaker,
 * - raportowanie po providerach.
 *
 * MockProviderClient jest szczególnie przydatny w projekcie edukacyjnym,
 * bo pozwala zrozumieć kontrakt między naszym systemem a PSP bez zależności
 * od prawdziwego API płatniczego.
 */
public class MockProviderClient implements PaymentProviderClient {

    /**
     * Provider, którego symuluje ta instancja klienta.
     *
     * Przykład:
     * - STRIPE_MOCK,
     * - ADYEN_MOCK,
     * - PAYU_MOCK.
     *
     * Jedna klasa może obsługiwać wielu mockowych providerów,
     * a różnica wynika z wartości przekazanej w konstruktorze.
     */
    private final PaymentProvider provider;

    public MockProviderClient(PaymentProvider provider) {
        this.provider = provider;
    }

    /**
     * Symuluje utworzenie płatności u zewnętrznego providera.
     *
     * W realnym systemie w tym miejscu byłoby wywołanie HTTP do PSP:
     * - z kwotą,
     * - walutą,
     * - merchantem,
     * - idempotency key,
     * - danymi checkoutu,
     * - callback URL.
     *
     * Tutaj zamiast requestu HTTP generujemy deterministyczny
     * providerPaymentId i lokalny checkoutUrl.
     *
     * Deterministyczny identyfikator jest ważny, bo ten sam request
     * z tym samym idempotency key powinien prowadzić do tego samego
     * identyfikatora po stronie mockowego providera.
     *
     * @param request dane płatności wysyłane do providera
     * @return odpowiedź mockowego PSP z ID płatności i URL checkoutu
     */
    @Override
    public PspPaymentResponse createPayment(PspPaymentRequest request) {

        /**
         * Budujemy źródło dla deterministycznego providerPaymentId.
         *
         * Używamy:
         * - nazwy providera,
         * - idempotencyKey, jeżeli istnieje,
         * - paymentId jako fallback.
         *
         * Dzięki temu:
         * - ten sam provider i ten sam idempotencyKey dadzą ten sam wynik,
         * - różni providerzy wygenerują różne ID,
         * - brak idempotencyKey nadal pozwala wygenerować stabilne ID
         *   na podstawie paymentId.
         */
        String source = provider.name()
                + ":"
                + (request.idempotencyKey() == null
                ? request.paymentId()
                : request.idempotencyKey());

        /**
         * Generujemy providerPaymentId.
         *
         * UUID.nameUUIDFromBytes() nie tworzy losowego UUID.
         * Tworzy UUID deterministycznie na podstawie bajtów wejściowych.
         *
         * To oznacza, że dla tego samego source zawsze dostaniemy
         * taki sam providerPaymentId.
         *
         * Prefix providera ułatwia debugowanie i raportowanie.
         *
         * Przykład:
         * stripe_mock_8a0f1c2e-...
         */
        String providerPaymentId = provider.name().toLowerCase()
                + "_"
                + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));

        /**
         * Tworzymy mockowy checkout URL.
         *
         * W realnym PSP byłby to adres, na który frontend przekierowuje klienta,
         * żeby dokończył płatność kartą, BLIKIEM, przelewem lub inną metodą.
         *
         * Tutaj URL jest lokalną symulacją i służy tylko do pokazania,
         * że PSP zwraca miejsce kontynuacji płatności.
         *
         * replace("_", "-") sprawia, że nazwa hosta wygląda jak poprawniejszy
         * adres domenowy, np. stripe-mock.local zamiast stripe_mock.local.
         */
        return new PspPaymentResponse(
                providerPaymentId,
                "https://"
                        + provider.name().toLowerCase().replace("_", "-")
                        + ".local/checkout/"
                        + providerPaymentId
        );
    }
}