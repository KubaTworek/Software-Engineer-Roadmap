package pl.jakubtworek.booking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.entity.CapacityPool;

import java.util.Optional;
import java.util.UUID;

/**
 * Repozytorium JPA dla encji CapacityPool.
 *
 * CapacityPool przechowuje dostępność miejsc dla eventu:
 *
 * - totalCapacity — całkowita liczba miejsc,
 * - availableCapacity — liczba miejsc nadal możliwych do zarezerwowania.
 *
 * To repozytorium jest krytyczne dla concurrency, ponieważ to tutaj znajdują się
 * metody pokazujące różne strategie ochrony przed oversellingiem:
 *
 * - zwykły odczyt po eventId,
 * - pessimistic lock,
 * - naiwny blind update,
 * - atomowy update zmniejszający dostępność,
 * - atomowy update zwiększający dostępność przy anulowaniu.
 */
public interface CapacityPoolRepository extends JpaRepository<CapacityPool, UUID> {

    /**
     * Pobiera pulę miejsc dla eventu bez żadnej dodatkowej blokady.
     *
     * To jest zwykły odczyt JPA.
     *
     * Samo użycie tej metody nie chroni przed race condition.
     * Jeśli zrobisz:
     *
     * 1. findByEventId(...)
     * 2. if available > 0
     * 3. available--
     * 4. save(...)
     *
     * to przy równoległych requestach możesz dostać lost update albo overselling,
     * zależnie od implementacji i izolacji transakcji.
     */
    Optional<CapacityPool> findByEventId(UUID eventId);

    /**
     * Pobiera pulę miejsc z blokadą pesymistyczną.
     *
     * @Lock(LockModeType.PESSIMISTIC_WRITE) oznacza, że Hibernate/JPA powinien
     * wykonać zapytanie z blokadą zapisu, np. w PostgreSQL jako:
     *
     * SELECT ...
     * FOR UPDATE
     *
     * Efekt:
     *
     * - pierwsza transakcja blokuje wybrany wiersz,
     * - inne transakcje próbujące pobrać ten sam wiersz do zapisu muszą czekać,
     * - zmniejsza to ryzyko lost update,
     * - ale może pogorszyć throughput przy dużej konkurencji.
     *
     * To dobra strategia, gdy naprawdę trzeba serializować dostęp do konkretnego
     * wiersza. Nie jest jednak darmowa — blokady mogą powodować lock wait,
     * contention, a przy złej kolejności blokad także deadlocki.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pool from CapacityPool pool where pool.event.id = :eventId")
    Optional<CapacityPool> findByEventIdForUpdate(@Param("eventId") UUID eventId);

    /**
     * Naiwna metoda ustawiająca dostępność na konkretną wartość.
     *
     * Nazwa blindSetAvailableCapacity jest celowo ostrzegawcza.
     * Ta metoda nie sprawdza:
     *
     * - czy dostępność jest dodatnia,
     * - czy ktoś inny nie zmienił wartości w międzyczasie,
     * - czy availableCapacity nie przekracza totalCapacity.
     *
     * Może być używana edukacyjnie do pokazania problemu lost update:
     *
     * 1. Transakcja A czyta available = 10.
     * 2. Transakcja B czyta available = 10.
     * 3. A zapisuje 9.
     * 4. B zapisuje 9.
     *
     * Dwie rezerwacje zostały utworzone, ale dostępność spadła tylko o 1.
     *
     * @Modifying mówi Spring Data JPA, że zapytanie zmienia dane, a nie jest SELECT-em.
     *
     * clearAutomatically = true czyści persistence context po update.
     * To ważne przy native update, bo Hibernate nie wie automatycznie, że dane
     * encji trzymanych w pamięci zostały zmienione bezpośrednio SQL-em.
     *
     * flushAutomatically = true wymusza flush przed wykonaniem update.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE capacity_pools
               SET available_capacity = :availableCapacity
             WHERE id = :poolId
            """, nativeQuery = true)
    int blindSetAvailableCapacity(
            @Param("poolId") UUID poolId,
            @Param("availableCapacity") int availableCapacity
    );

    /**
     * Atomowo próbuje zarezerwować jedno miejsce.
     *
     * To jest najważniejsza metoda chroniąca przed oversellingiem.
     *
     * Zapytanie robi check-and-update w jednej operacji SQL:
     *
     * UPDATE capacity_pools
     * SET available_capacity = available_capacity - 1
     * WHERE event_id = :eventId
     *   AND available_capacity > 0
     *
     * Jeżeli available_capacity > 0, baza zmniejsza wartość i zwraca liczbę
     * zmienionych wierszy = 1.
     *
     * Jeżeli miejsc nie ma, warunek WHERE nie pasuje i liczba zmienionych wierszy = 0.
     *
     * Dzięki temu nie ma osobnego okna czasowego między:
     *
     * - sprawdzeniem dostępności,
     * - a zmniejszeniem dostępności.
     *
     * To eliminuje klasyczny problem check-then-act w Javie.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE capacity_pools
               SET available_capacity = available_capacity - 1
             WHERE event_id = :eventId
               AND available_capacity > 0
            """, nativeQuery = true)
    int reserveOneSeatIfAvailable(@Param("eventId") UUID eventId);

    /**
     * Atomowo zwalnia jedno miejsce przy anulowaniu rezerwacji.
     *
     * Zapytanie zwiększa available_capacity tylko wtedy, gdy obecna dostępność
     * jest mniejsza niż total_capacity:
     *
     * UPDATE capacity_pools
     * SET available_capacity = available_capacity + 1
     * WHERE event_id = :eventId
     *   AND available_capacity < total_capacity
     *
     * Ten warunek chroni przed sytuacją, w której przez błąd aplikacji dostępność
     * przekroczyłaby całkowitą pojemność eventu.
     *
     * Przykład:
     *
     * - totalCapacity = 10,
     * - availableCapacity = 10,
     * - błędna logika próbuje jeszcze raz zwolnić miejsce.
     *
     * Bez warunku available_capacity < total_capacity moglibyśmy dostać 11/10.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE capacity_pools
               SET available_capacity = available_capacity + 1
             WHERE event_id = :eventId
               AND available_capacity < total_capacity
            """, nativeQuery = true)
    int releaseOneSeat(@Param("eventId") UUID eventId);
}