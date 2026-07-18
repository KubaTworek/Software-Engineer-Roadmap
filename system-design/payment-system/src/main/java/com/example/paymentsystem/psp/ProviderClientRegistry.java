package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rejestr klientów PSP.
 *
 * ProviderClientRegistry mapuje PaymentProvider na konkretną implementację
 * PaymentProviderClient.
 *
 * Dzięki temu PaymentService nie musi tworzyć klientów PSP ręcznie
 * i nie musi znać klas implementacyjnych takich jak MockProviderClient.
 *
 * PaymentService może zrobić:
 * - wybierz providera przez ProviderRoutingService,
 * - pobierz klienta PSP z ProviderClientRegistry,
 * - wywołaj createPayment().
 *
 * W produkcyjnym systemie ten rejestr mógłby mapować providerów na realne
 * integracje:
 * - STRIPE -> StripeProviderClient,
 * - ADYEN -> AdyenProviderClient,
 * - PAYU -> PayuProviderClient.
 *
 * W tym projekcie wszystkie integracje są mockowe,
 * ale każda instancja MockProviderClient symuluje innego providera.
 */
@Component
public class ProviderClientRegistry {

    /**
     * Mapa provider -> klient PSP.
     *
     * Używamy EnumMap, bo kluczem jest enum PaymentProvider.
     *
     * EnumMap jest dobrym wyborem dla enumów, ponieważ:
     * - jest prostszy i szybszy niż zwykły HashMap dla kluczy enum,
     * - zachowuje typowanie po PaymentProvider,
     * - jasno pokazuje, że zestaw kluczy jest ograniczony do wartości enum.
     */
    private final Map<PaymentProvider, PaymentProviderClient> clients =
            new EnumMap<>(PaymentProvider.class);

    /**
     * Buduje rejestr klientów PSP.
     *
     * Flow:
     * 1. Iterujemy po wszystkich wartościach PaymentProvider.
     * 2. Dla każdego providera tworzymy MockProviderClient.
     * 3. Zapisujemy klienta w mapie pod kluczem providera.
     *
     * Dzięki temu dodanie nowej wartości do enum PaymentProvider
     * automatycznie tworzy dla niej mockowego klienta.
     *
     * To jest wygodne w projekcie edukacyjnym.
     * W produkcji raczej nie tworzylibyśmy integracji w pętli,
     * tylko wstrzykiwali konkretne beany Springa dla każdego PSP.
     */
    public ProviderClientRegistry() {
        for (PaymentProvider provider : PaymentProvider.values()) {
            clients.put(provider, new MockProviderClient(provider));
        }
    }

    /**
     * Zwraca klienta PSP dla wskazanego providera.
     *
     * Ta metoda jest używana po routingu.
     *
     * Przykład flow:
     * - ProviderRoutingService wybiera PAYU_MOCK,
     * - ProviderClientRegistry zwraca klienta dla PAYU_MOCK,
     * - PaymentService wywołuje createPayment() na tym kliencie.
     *
     * @param provider provider wybrany do obsługi płatności
     * @return klient PSP przypisany do providera
     */
    public PaymentProviderClient get(PaymentProvider provider) {
        return clients.get(provider);
    }
}