package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.entity.Event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repozytorium JPA dla encji Event.
 *
 * JpaRepository<Event, UUID> daje gotowe operacje CRUD:
 *
 * - save(...),
 * - findById(...),
 * - existsById(...),
 * - delete(...),
 * - findAll(...).
 *
 * Event ma identyfikator typu UUID, dlatego drugim parametrem generycznym
 * jest UUID.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * Wyszukuje eventy po konkretnym access patternie:
     *
     * - miasto,
     * - kategoria,
     * - data rozpoczęcia większa lub równa podanej wartości,
     * - sortowanie po dacie rozpoczęcia rosnąco.
     *
     * To zapytanie zostało dodane w etapie SQL/performance, bo dobrze pokazuje
     * sens indeksu złożonego:
     *
     * CREATE INDEX idx_event_city_category_start_time
     * ON events(city, category, starts_at);
     *
     * Dlaczego taka kolejność kolumn ma sens?
     *
     * - city jest filtrem równościowym,
     * - category też jest filtrem równościowym,
     * - starts_at jest filtrem zakresowym oraz kolumną sortowania.
     *
     * Baza może użyć indeksu do szybkiego znalezienia eventów dla konkretnego
     * miasta i kategorii, a potem przejść po zakresie dat.
     *
     * Uwaga: to jest JPQL, nie czysty SQL.
     *
     * Dlatego używamy nazw encji i pól Javy:
     *
     * - Event,
     * - event.city,
     * - event.category,
     * - event.startsAt.
     *
     * Nie używamy tutaj nazw tabel i kolumn:
     *
     * - events,
     * - city,
     * - category,
     * - starts_at.
     */
    @Query("""
            select event
              from Event event
             where event.city = :city
               and event.category = :category
               and event.startsAt >= :from
             order by event.startsAt asc
            """)
    List<Event> searchByCityCategoryAndFrom(
            /**
             * Parametr :city z zapytania JPQL.
             *
             * Spring podstawia tu wartość przekazaną do metody.
             */
            @Param("city") String city,

            /**
             * Parametr :category z zapytania JPQL.
             */
            @Param("category") String category,

            /**
             * Parametr :from z zapytania JPQL.
             *
             * OffsetDateTime pozwala reprezentować datę razem ze strefą/offsetem.
             * Trzeba tylko konsekwentnie pilnować mapowania czasu w bazie danych.
             */
            @Param("from") OffsetDateTime from
    );
}