package com.ridesharing.warehouse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Kontroler analityczny dla prostego Data Warehouse.
 *
 * W Etapie 3/4 aplikacji ride-sharing warehouse zbiera eventy z systemu,
 * np. z Kafki, outboxa albo consumerów integracyjnych.
 *
 * Ten controller udostępnia lekkie endpointy raportowe nad tabelą fact_events.
 * Nie obsługuje flow pasażera ani kierowcy. Służy do podglądu agregatów:
 * - ile eventów przyszło z danego topicu,
 * - ile eventów dotyczy danego miasta.
 */
@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseController {

    /**
     * JdbcTemplate do wykonywania prostych zapytań SQL na bazie warehouse.
     *
     * W tej klasie nie używamy JPA, bo endpointy są typowo analityczne:
     * operują na agregacjach, countach i tabelach faktów.
     *
     * JdbcTemplate jest tu wystarczający i czytelny.
     */
    private final JdbcTemplate jdbc;

    /**
     * Konstruktor wstrzykujący JdbcTemplate.
     *
     * Controller nie tworzy połączenia do bazy samodzielnie.
     * Korzysta z datasource skonfigurowanego przez Spring Boot.
     */
    public WarehouseController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Zwraca liczbę eventów pogrupowaną po topicu źródłowym.
     *
     * Endpoint:
     * GET /api/v1/warehouse/events/count-by-topic
     *
     * Przykładowe topic'i:
     * - ride.events,
     * - payment.events,
     * - support.events,
     * - driver.location.updated.
     *
     * To pozwala szybko sprawdzić, które strumienie generują najwięcej danych
     * i czy pipeline eventów faktycznie zapisuje dane do warehouse.
     */
    @GetMapping("/events/count-by-topic")
    List<Map<String, Object>> countByTopic() {
        return jdbc.queryForList(
                """
                SELECT source_topic, count(*) AS total
                FROM fact_events
                GROUP BY source_topic
                ORDER BY total DESC
                """
        );
    }

    /**
     * Zwraca liczbę eventów pogrupowaną po city_id.
     *
     * Endpoint:
     * GET /api/v1/warehouse/events/count-by-city
     *
     * To jest przydatne w ride-sharingu, bo większość operacji jest lokalna:
     * przejazdy, matching, lokalizacje i pricing są naturalnie powiązane z miastem.
     *
     * coalesce(city_id, 'unknown') grupuje eventy bez miasta jako "unknown",
     * żeby nie ginęły w raportowaniu.
     */
    @GetMapping("/events/count-by-city")
    List<Map<String, Object>> countByCity() {
        return jdbc.queryForList(
                """
                SELECT coalesce(city_id, 'unknown') AS city_id, count(*) AS total
                FROM fact_events
                GROUP BY city_id
                ORDER BY total DESC
                """
        );
    }
}