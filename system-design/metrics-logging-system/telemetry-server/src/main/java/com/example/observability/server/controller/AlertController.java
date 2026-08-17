package com.example.observability.server.controller;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import com.example.observability.server.alert.AlertRuleStore;
import com.example.observability.server.auth.Rbac;
import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API do zarządzania alertami.
 *
 * Ten controller obsługuje dwie główne rzeczy:
 *
 * 1. Reguły alertowe:
 *    - tworzenie,
 *    - listowanie,
 *    - usuwanie.
 *
 * 2. Eventy alertowe:
 *    - odczyt historii wygenerowanych alertów.
 *
 * Sama ewaluacja alertów nie dzieje się w tym controllerze.
 * Controller tylko zapisuje/odczytuje konfigurację i historię.
 * Właściwe sprawdzanie reguł powinno być wykonywane przez osobny scheduler/evaluator.
 */
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    /**
     * Store reguł alertowych.
     *
     * Odpowiada za zapis, odczyt i usuwanie definicji alertów.
     * Może być implementowany w pamięci, w ClickHouse, PostgreSQL
     * albo innym trwałym storage'u zależnie od wersji projektu.
     */
    private final AlertRuleStore store;

    /**
     * Repozytorium telemetryczne używane tutaj do odczytu eventów alertowych.
     *
     * AlertEvent to rezultat działania alert evaluatora,
     * np. informacja, że konkretna reguła weszła w stan FIRING.
     */
    private final TelemetryRepository repository;

    public AlertController(AlertRuleStore store, TelemetryRepository repository) {
        this.store = store;
        this.repository = repository;
    }

    /**
     * Tworzy albo zapisuje regułę alertową.
     *
     * Endpoint:
     * POST /api/v1/alerts/rules
     *
     * Przykładowa reguła może opisywać:
     * - nazwę alertu,
     * - tenantId,
     * - typ źródła danych,
     * - query,
     * - próg,
     * - severity,
     * - routing powiadomień.
     *
     * Wymagane jest uprawnienie admina dla tenantId z reguły.
     * To ważne, bo reguła alertowa może generować kosztowne query
     * oraz wysyłać powiadomienia do zewnętrznych systemów.
     */
    @PostMapping("/rules")
    public AlertRule create(@RequestBody AlertRule rule) {
        Rbac.requireAdmin(rule.getTenantId());
        return store.save(rule);
    }

    /**
     * Zwraca listę reguł alertowych dla konkretnego tenanta.
     *
     * Endpoint:
     * GET /api/v1/alerts/rules?tenantId=demo
     *
     * Domyślnie tenantId=demo, co jest wygodne dla środowiska demo,
     * ale w produkcji lepiej wymagać jawnego tenantId albo wyciągać go
     * z kontekstu autoryzacji.
     *
     * Rbac.requireRead(tenantId) pozwala czytać reguły tylko użytkownikom,
     * którzy mają dostęp do danego tenanta.
     */
    @GetMapping("/rules")
    public List<AlertRule> list(
            @RequestParam(defaultValue = "demo") String tenantId
    ) {
        Rbac.requireRead(tenantId);

        // Store może zawierać reguły wielu tenantów, dlatego filtrujemy po tenantId.
        return store.all()
                .stream()
                .filter(rule -> tenantId.equals(rule.getTenantId()))
                .toList();
    }

    /**
     * Usuwa regułę alertową po jej identyfikatorze.
     *
     * Endpoint:
     * DELETE /api/v1/alerts/rules/{id}?tenantId=demo
     *
     * Wymaga admina dla tenantId podanego w parametrze.
     *
     * Ważna uwaga:
     * W obecnej implementacji metoda ufa tenantId z request param
     * i usuwa regułę tylko po id.
     *
     * Produkcyjnie warto najpierw pobrać regułę po id,
     * sprawdzić jej faktyczny tenantId,
     * wykonać Rbac.requireAdmin(rule.getTenantId()),
     * i dopiero wtedy ją usunąć.
     *
     * Inaczej admin jednego tenanta mógłby potencjalnie próbować usunąć
     * regułę innego tenanta, jeśli zna jej id.
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestParam(defaultValue = "demo") String tenantId
    ) {
        Rbac.requireAdmin(tenantId);
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Zwraca historię eventów alertowych dla konkretnego tenanta.
     *
     * Endpoint:
     * GET /api/v1/alerts/events?tenantId=demo&limit=100
     *
     * Eventy są generowane przez alert evaluator,
     * a nie przez ten controller.
     *
     * Typowe eventy:
     * - alert firing,
     * - alert resolved,
     * - alert evaluation error,
     * - notification sent/failed.
     *
     * limit ogranicza liczbę zwracanych rekordów,
     * co chroni API przed przypadkowym pobraniem zbyt dużej historii.
     */
    @GetMapping("/events")
    public List<AlertEvent> events(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(required = false) Integer limit
    ) {
        Rbac.requireRead(tenantId);
        return repository.queryAlertEvents(tenantId, limit);
    }
}