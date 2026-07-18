package com.example.paymentsystem.psp;

/**
 * Wspólny kontrakt dla klientów PSP.
 *
 * PSP oznacza Payment Service Provider, czyli zewnętrznego operatora płatności,
 * np. Stripe, Adyen, PayU albo lokalny bankowy provider.
 *
 * PaymentProviderClient ukrywa szczegóły konkretnej integracji.
 * Dzięki temu PaymentService nie musi wiedzieć:
 * - czy provider używa REST API,
 * - jak wygląda jego payload,
 * - jak nazywa swoje pola,
 * - jak buduje checkout URL,
 * - jak zwraca identyfikator płatności.
 *
 * PaymentService zna tylko jeden wspólny kontrakt:
 * wysyłam PspPaymentRequest i dostaję PspPaymentResponse.
 *
 * To ułatwia dodawanie kolejnych providerów bez przepisywania głównego flow płatności.
 */
public interface PaymentProviderClient {

    /**
     * Tworzy płatność po stronie providera.
     *
     * W realnym systemie implementacja tej metody wykonałaby request HTTP
     * do konkretnego PSP.
     *
     * Przykładowe odpowiedzialności implementacji:
     * - mapowanie naszego requestu na format providera,
     * - przekazanie idempotency key,
     * - obsługa timeoutów,
     * - obsługa błędów PSP,
     * - odczyt providerPaymentId,
     * - odczyt checkoutUrl.
     *
     * @param request dane płatności w naszym wewnętrznym formacie
     * @return odpowiedź providera z identyfikatorem płatności i URL checkoutu
     */
    PspPaymentResponse createPayment(PspPaymentRequest request);
}