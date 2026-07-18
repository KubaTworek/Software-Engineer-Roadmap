package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za wybór PSP dla nowej płatności.
 *
 * Routing providera decyduje, do którego Payment Service Providera
 * system wyśle request o utworzenie płatności.
 *
 * W realnym Payment Systemie routing może zależeć od wielu czynników:
 * - waluty,
 * - kraju klienta,
 * - kraju merchanta,
 * - metody płatności,
 * - prowizji PSP,
 * - dostępności providera,
 * - skuteczności autoryzacji,
 * - limitów transakcyjnych,
 * - reguł biznesowych merchanta.
 *
 * W tej wersji routing jest prosty:
 * - PLN kierujemy do PAYU_MOCK,
 * - EUR kierujemy do ADYEN_MOCK,
 * - pozostałe waluty kierujemy do STRIPE_MOCK.
 *
 * Następnie sprawdzamy circuit breaker.
 * Jeżeli preferowany PSP jest niedostępny, wybieramy pierwszy dostępny fallback.
 */
@Service
public class ProviderRoutingService {

    /**
     * CircuitBreakerService informuje routing,
     * czy dany provider jest aktualnie dostępny.
     *
     * Dzięki temu routing nie wysyła nowych płatności
     * do PSP oznaczonego jako OPEN.
     */
    private final CircuitBreakerService circuitBreakerService;

    public ProviderRoutingService(CircuitBreakerService circuitBreakerService) {
        this.circuitBreakerService = circuitBreakerService;
    }

    /**
     * Wybiera providera dla płatności.
     *
     * Flow:
     * 1. Wybieramy preferowanego PSP na podstawie waluty.
     * 2. Sprawdzamy, czy preferowany PSP jest dostępny.
     * 3. Jeżeli jest dostępny, zwracamy go.
     * 4. Jeżeli nie jest dostępny, szukamy fallbacku.
     * 5. Jeżeli żaden PSP nie działa, rzucamy wyjątek.
     *
     * @param currency waluta płatności
     * @param amount kwota płatności w najmniejszej jednostce waluty
     * @return provider, który powinien obsłużyć płatność
     */
    public PaymentProvider route(String currency, long amount) {

        /**
         * Najpierw wybieramy preferowanego providera.
         *
         * To jest podstawowa reguła biznesowa routingu.
         *
         * PLN:
         * - lokalny provider PayU jest naturalnym wyborem dla polskiego rynku.
         *
         * EUR:
         * - Adyen często obsługuje międzynarodowe i europejskie flow.
         *
         * Pozostałe waluty:
         * - Stripe traktujemy jako domyślnego globalnego providera.
         */
        PaymentProvider preferred;
        if ("PLN".equals(currency)) {
            preferred = PaymentProvider.PAYU_MOCK;
        } else if ("EUR".equals(currency)) {
            preferred = PaymentProvider.ADYEN_MOCK;
        } else {
            preferred = PaymentProvider.STRIPE_MOCK;
        }

        /**
         * Sprawdzamy, czy preferowany provider jest dostępny.
         *
         * Jeżeli jego circuit breaker jest CLOSED,
         * możemy wysłać płatność do tego PSP.
         *
         * To jest najlepszy scenariusz, bo zachowujemy standardową
         * regułę routingu dla danej waluty.
         */
        if (circuitBreakerService.isAvailable(preferred)) {
            return preferred;
        }

        /**
         * Jeżeli preferowany provider jest niedostępny,
         * szukamy fallbacku.
         *
         * Fallback oznacza, że płatność może zostać obsłużona
         * przez innego PSP niż domyślny dla danej waluty.
         *
         * Dzięki temu awaria jednego providera nie zatrzymuje
         * całego systemu płatności.
         */
        for (PaymentProvider fallback : PaymentProvider.values()) {

            /**
             * Zwracamy pierwszego providera, którego circuit breaker
             * nadal jest CLOSED.
             *
             * Kolejność fallbacków wynika z kolejności enum PaymentProvider.
             * W produkcji fallback order powinien być jawnie skonfigurowany,
             * żeby nie zależeć przypadkowo od kolejności wartości enum.
             */
            if (circuitBreakerService.isAvailable(fallback)) {
                return fallback;
            }
        }

        /**
         * Jeżeli żaden provider nie jest dostępny,
         * system nie może bezpiecznie utworzyć nowej płatności.
         *
         * Lepiej zwrócić kontrolowany błąd niż wysyłać requesty
         * do PSP, o których wiemy, że są niedostępni.
         */
        throw new ProviderUnavailableException("All payment providers are unavailable");
    }
}