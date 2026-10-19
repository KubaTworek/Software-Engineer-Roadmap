package pl.jakubtworek.backend.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.backend.reservation.domain.EventInventoryEntity;

import java.util.UUID;

public interface EventInventoryRepository extends JpaRepository<EventInventoryEntity, UUID> {
    /**
     * Idempotentny bootstrap projekcji pozwala obsłużyć wydarzenie dodane po migracji seedów.
     * ON CONFLICT DO NOTHING jest kluczowe: ponowny snapshot z Catalog Service nie może
     * nadpisać liczby biletów pomniejszonej już przez lokalne rezerwacje.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO event_inventory(event_id, total_tickets, available_tickets) " +
            "VALUES (:eventId, :totalTickets, :totalTickets) ON CONFLICT (event_id) DO NOTHING",
            nativeQuery = true)
    int initializeIfAbsent(@Param("eventId") UUID eventId, @Param("totalTickets") int totalTickets);

    /**
     * Zmiana pojemności zachowuje liczbę już przydzielonych miejsc. expectedVersion chroni
     * operatorów przed lost update, a ostatni warunek zabrania zmniejszenia puli poniżej
     * `reserved = total - available`.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE event_inventory SET available_tickets=available_tickets+(:newTotal-total_tickets), " +
            "total_tickets=:newTotal, version=version+1 WHERE event_id=:eventId AND version=:expectedVersion " +
            "AND :newTotal >= total_tickets-available_tickets", nativeQuery = true)
    int changeCapacity(@Param("eventId") UUID eventId, @Param("expectedVersion") long expectedVersion,
                       @Param("newTotal") int newTotal);

    /**
     * Check-and-decrement jest jedną instrukcją PostgreSQL. Naiwne "SELECT, sprawdź,
     * UPDATE" ma okno wyścigu; tutaj wynik 1 oznacza zdobycie pojemności, a 0 jej brak.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE event_inventory SET available_tickets = available_tickets - :quantity " +
            "WHERE event_id = :eventId AND available_tickets >= :quantity", nativeQuery = true)
    int reserve(@Param("eventId") UUID eventId, @Param("quantity") int quantity);

    /** Zwolnienie pojemności odbywa się w tej samej transakcji co zmiana rezerwacji. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE event_inventory SET available_tickets = available_tickets + :quantity " +
            "WHERE event_id = :eventId AND available_tickets + :quantity <= total_tickets", nativeQuery = true)
    int release(@Param("eventId") UUID eventId, @Param("quantity") int quantity);

    /**
     * Chroniony zasób sprawdza token, zamiast ufać samemu lease. Ten sam aktualny token
     * może zwolnić wiele rezerwacji; token starszy od ostatnio zaakceptowanego jest odrzucany.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE event_inventory SET available_tickets=available_tickets+:quantity, " +
            "last_fencing_token=:token WHERE event_id=:eventId AND last_fencing_token <= :token " +
            "AND available_tickets+:quantity <= total_tickets", nativeQuery = true)
    int releaseWithFencing(@Param("eventId") UUID eventId, @Param("quantity") int quantity,
                           @Param("token") long token);
}
