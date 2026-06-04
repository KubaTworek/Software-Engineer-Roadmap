package pl.jakubtworek.marketplace.payment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.payment.application.PaymentRepository;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.payment.domain.PaymentId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja portu PaymentRepository.
 *
 * Ta klasa należy do warstwy infrastruktury modułu Payment.
 * Implementuje port z warstwy aplikacyjnej, ale nie powinna być znana domenie.
 *
 * Jest to prosta implementacja przydatna na początkowym etapie projektu:
 * - pozwala testować flow płatności bez bazy danych,
 * - upraszcza testy jednostkowe i komponentowe,
 * - pozwala lokalnie uruchomić aplikację bez PostgreSQL.
 *
 * Nie jest to implementacja produkcyjna.
 * Dane są przechowywane wyłącznie w pamięci procesu i znikają po restarcie aplikacji.
 */
@Repository
@Profile("!postgres")
public class InMemoryPaymentRepository implements PaymentRepository {

    /**
     * Prosty magazyn płatności w pamięci.
     *
     * Kluczem jest domenowy PaymentId, a wartością agregat Payment.
     *
     * ConcurrentHashMap zabezpiecza samą strukturę mapy przed podstawowymi problemami
     * współbieżności, ale nie daje transakcyjności ani izolacji takiej jak baza danych.
     */
    private final Map<PaymentId, Payment> payments = new ConcurrentHashMap<>();

    /**
     * Zapisuje agregat Payment w pamięci.
     *
     * Jeśli płatność o tym samym PaymentId już istnieje, zostanie nadpisana.
     *
     * Ta implementacja przechowuje referencję do obiektu Payment, a nie jego kopię.
     * Oznacza to, że zmiana pobranego obiektu może być widoczna w mapie nawet bez
     * ponownego wywołania save(...). To różni się od typowego zachowania bazy danych.
     */
    @Override
    public Payment save(Payment payment) {
        payments.put(payment.id(), payment);
        return payment;
    }

    /**
     * Wyszukuje płatność po identyfikatorze zamówienia.
     *
     * W tym modelu płatność jest powiązana z zamówieniem przez orderId.
     * Zwracamy Optional, ponieważ dla danego zamówienia płatność może jeszcze nie istnieć,
     * np. zanim handler Payment zareaguje na OrderPlaced.
     *
     * Uwaga techniczna:
     * - ta implementacja przeszukuje wszystkie wartości mapy liniowo,
     * - dla implementacji in-memory i małej liczby danych to wystarczy,
     * - w bazie danych warto mieć indeks po order_id.
     */
    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return payments.values().stream()
                .filter(payment -> payment.orderId().equals(orderId))
                .findFirst();
    }
}