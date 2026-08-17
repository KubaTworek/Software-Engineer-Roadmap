package com.example.paymentsystem.psp;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis zarządzający stanem circuit breakera dla providerów płatności.
 *
 * Circuit breaker chroni system przed ciągłym wysyłaniem requestów
 * do PSP, który aktualnie nie działa albo zwraca serię błędów.
 *
 * Bez tego mechanizmu awaria jednego providera mogłaby powodować:
 * - kolejne timeouty,
 * - blokowanie wątków aplikacji,
 * - rosnące opóźnienia,
 * - przeciążenie PSP i naszej aplikacji,
 * - niepotrzebne błędy płatności.
 *
 * Stan providera jest przechowywany w bazie, dzięki czemu może być
 * współdzielony między wieloma instancjami aplikacji.
 */
@Service
public class CircuitBreakerService {

    /**
     * Repozytorium przechowujące aktualny stan każdego PSP:
     * - CLOSED — provider jest dostępny,
     * - OPEN — provider jest wyłączony z routingu,
     * - liczba kolejnych błędów,
     * - czas otwarcia circuit breakera.
     */
    private final ProviderHealthRepository repository;

    /**
     * Liczba błędów, po której circuit breaker przechodzi w stan OPEN.
     *
     * Wartość pochodzi z konfiguracji:
     *
     * payment-system.circuit-breaker.failure-threshold
     *
     * Domyślna wartość to 3.
     */
    private final int threshold;

    public CircuitBreakerService(
            ProviderHealthRepository repository,
            @Value("${payment-system.circuit-breaker.failure-threshold:3}") int threshold
    ) {
        this.repository = repository;
        this.threshold = threshold;
    }

    /**
     * Sprawdza, czy provider może aktualnie obsłużyć płatność.
     *
     * Provider jest dostępny tylko wtedy, gdy jego circuit breaker
     * znajduje się w stanie CLOSED.
     *
     * Jeżeli provider nie ma jeszcze rekordu w bazie, uznajemy go
     * za dostępnego. Oznacza to, że nowy PSP może zostać użyty
     * bez wcześniejszego inicjalizowania ProviderHealth.
     *
     * Ta metoda jest wykorzystywana przez routing płatności.
     * Jeżeli preferowany PSP jest niedostępny, routing wybiera fallback.
     *
     * @param provider PSP, którego stan sprawdzamy
     * @return true, jeżeli provider może być użyty
     */
    @Transactional(readOnly = true)
    public boolean isAvailable(PaymentProvider provider) {
        return repository.findById(provider)
                .map(health -> health.getStatus() == ProviderHealthStatus.CLOSED)
                .orElse(true);
    }

    /**
     * Rejestruje udane wywołanie providera.
     *
     * Sukces:
     * - zeruje licznik wcześniejszych błędów,
     * - ustawia circuit breaker na CLOSED,
     * - przywraca providera do routingu.
     *
     * Jeżeli rekord ProviderHealth jeszcze nie istnieje,
     * zostaje utworzony przy pierwszym sukcesie.
     *
     * @param provider PSP, który poprawnie obsłużył request
     */
    @Transactional
    public void success(PaymentProvider provider) {
        ProviderHealth health = repository.findById(provider)
                .orElseGet(() -> new ProviderHealth(provider));

        health.recordSuccess();

        repository.save(health);
    }

    /**
     * Rejestruje błąd wywołania providera.
     *
     * Każdy błąd zwiększa failureCount.
     * Gdy liczba błędów osiągnie threshold, ProviderHealth
     * przechodzi w stan OPEN.
     *
     * Provider ze stanem OPEN nie powinien być wybierany
     * przez ProviderRoutingService.
     *
     * Przykład dla threshold = 3:
     * - pierwszy błąd: failureCount = 1, CLOSED,
     * - drugi błąd: failureCount = 2, CLOSED,
     * - trzeci błąd: failureCount = 3, OPEN.
     *
     * @param provider PSP, którego wywołanie zakończyło się błędem
     */
    @Transactional
    public void failure(PaymentProvider provider) {
        ProviderHealth health = repository.findById(provider)
                .orElseGet(() -> new ProviderHealth(provider));

        health.recordFailure(threshold);

        repository.save(health);
    }

    /**
     * Ręcznie zmienia stan circuit breakera.
     *
     * Metoda jest używana przez endpoint administracyjny.
     * Pozwala operatorowi:
     * - wyłączyć PSP z routingu przed planowanymi pracami,
     * - odciąć providera przy wykrytej awarii,
     * - ponownie włączyć providera po naprawie.
     *
     * OPEN:
     * provider nie będzie wybierany do nowych płatności.
     *
     * CLOSED:
     * provider ponownie może brać udział w routingu,
     * a licznik błędów zostaje wyzerowany przez forceClose().
     *
     * @param provider PSP, którego stan zmieniamy
     * @param status docelowy stan circuit breakera
     */
    @Transactional
    public void force(
            PaymentProvider provider,
            ProviderHealthStatus status
    ) {
        ProviderHealth health = repository.findById(provider)
                .orElseGet(() -> new ProviderHealth(provider));

        if (status == ProviderHealthStatus.OPEN) {
            health.forceOpen();
        } else {
            health.forceClose();
        }

        repository.save(health);
    }
}