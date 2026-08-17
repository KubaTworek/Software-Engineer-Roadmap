package com.example.observability.server.alert;

import com.example.observability.server.repository.TelemetryRepository;
import com.example.observability.server.notification.AlertRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Cykliczny evaluator reguł alertowych.
 *
 * To jest komponent backendowy, a nie REST controller.
 * Spring uruchamia go automatycznie zgodnie z harmonogramem z konfiguracji:
 *
 * telemetry.alerting.evaluation-interval-ms
 *
 * Główne zadanie:
 * - pobrać aktywne reguły alertowe,
 * - policzyć aktualną wartość metryki dla okna czasowego,
 * - porównać wartość z progiem,
 * - zapisać AlertEvent,
 * - przekazać alert do routera powiadomień.
 *
 * Ten komponent odpowiada za realne wykrywanie alertów.
 */
@Component
public class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    /**
     * Store z definicjami reguł alertowych.
     *
     * Źródło prawdy dla alertów:
     * - które reguły istnieją,
     * - które są włączone,
     * - jaki mają próg,
     * - jaki operator,
     * - jakie okno czasowe,
     * - do jakiego tenanta należą.
     */
    private final AlertRuleStore ruleStore;

    /**
     * Repozytorium telemetryczne.
     *
     * Używane do dwóch rzeczy:
     * 1. policzenia wartości metryki dla reguły,
     * 2. zapisania wygenerowanego AlertEvent.
     */
    private final TelemetryRepository repository;

    /**
     * Router powiadomień alertowych.
     *
     * Odpowiada za dostarczenie alertu do skonfigurowanych kanałów,
     * np. log, webhook, Slack, PagerDuty, email.
     *
     * Evaluator nie powinien znać szczegółów kanałów.
     * On tylko mówi: "ta reguła fire'uje, obsłuż routing".
     */
    private final AlertRouter alertRouter;

    public AlertEvaluator(
            AlertRuleStore ruleStore,
            TelemetryRepository repository,
            AlertRouter alertRouter
    ) {
        this.ruleStore = ruleStore;
        this.repository = repository;
        this.alertRouter = alertRouter;
    }

    /**
     * Główna metoda ewaluująca wszystkie reguły.
     *
     * Uruchamiana cyklicznie przez Spring Scheduler.
     *
     * fixedDelay oznacza:
     * - kolejny przebieg zacznie się dopiero po zakończeniu poprzedniego,
     * - system nie uruchomi równolegle wielu ewaluacji tej samej instancji,
     * - przy wolnych query realny odstęp między przebiegami będzie większy.
     *
     * To bezpieczniejsze niż fixedRate dla prostego MVP,
     * bo nie dokłada kolejnych przebiegów, gdy poprzedni jeszcze trwa.
     */
    @Scheduled(fixedDelayString = "${telemetry.alerting.evaluation-interval-ms}")
    public void evaluateRules() {

        /*
         * Iterujemy po wszystkich regułach.
         *
         * W większym systemie warto byłoby:
         * - shardingować reguły,
         * - filtrować po tenantach,
         * - wykonywać ewaluację równolegle,
         * - mieć per-rule timeout.
         */
        for (AlertRule rule : ruleStore.all()) {

            // Wyłączone reguły są pomijane bez wykonywania query.
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                Instant end = Instant.now();

                /*
                 * Okno ewaluacji pochodzi z reguły.
                 *
                 * Przykład:
                 * windowSeconds = 300 oznacza:
                 * "sprawdź metrykę z ostatnich 5 minut".
                 */
                Instant start = end.minusSeconds(rule.getWindowSeconds());

                /*
                 * Repository wykonuje właściwe zapytanie metryczne.
                 *
                 * AlertEvaluator nie zna szczegółów query language ani storage'u.
                 * Dostaje jedną wartość liczbową, którą porówna z progiem.
                 */
                double value = repository.evaluateMetric(rule, start, end);

                /*
                 * Sprawdzenie warunku alertowego.
                 *
                 * Operator i threshold pochodzą z reguły, np.:
                 * - value > 0.05
                 * - value >= 100
                 * - value < 1
                 */
                boolean firing = compare(
                        value,
                        rule.getOperator(),
                        rule.getThreshold()
                );

                if (firing) {
                    /*
                     * AlertEvent to materializacja faktu, że reguła weszła
                     * w stan FIRING w konkretnym czasie.
                     *
                     * Event jest zapisywany do storage'u, żeby dało się:
                     * - pokazać historię alertów,
                     * - debugować alerting,
                     * - budować dashboardy i audyt.
                     */
                    AlertEvent event = new AlertEvent(
                            rule.getTenantId(),
                            rule.getId(),
                            rule.getName(),
                            "FIRING",
                            end,
                            value,
                            rule.getThreshold(),
                            "Rule " + rule.getName()
                                    + " is firing: observed=" + value
                    );

                    repository.insertAlertEvent(event);

                    /*
                     * Routing powiadomień jest oddzielony od ewaluacji.
                     *
                     * Dzięki temu evaluator nie musi wiedzieć,
                     * czy alert ma trafić do webhooka, Slacka, e-maila
                     * czy tylko do logów.
                     */
                    alertRouter.route(rule, event);

                    log.warn(
                            "ALERT FIRING: {} observed={} threshold={}",
                            rule.getName(),
                            value,
                            rule.getThreshold()
                    );
                }

            } catch (Exception e) {
                /*
                 * Błąd jednej reguły nie może zatrzymać ewaluacji pozostałych.
                 *
                 * To ważne operacyjnie:
                 * - jedna zła reguła,
                 * - timeout query,
                 * - błąd storage'u,
                 * nie powinny wyłączać całego alertingu.
                 */
                log.error("Alert evaluation failed for rule {}", rule.getName(), e);
            }
        }
    }

    /**
     * Porównuje obliczoną wartość metryki z progiem zdefiniowanym w regule.
     *
     * Obsługiwane operatory:
     * - >
     * - >=
     * - <
     * - <=
     * - ==
     * - !=
     *
     * Dla nieznanego operatora fallbackiem jest ">".
     * To jest wygodne dla MVP, ale produkcyjnie lepiej odrzucać błędny operator
     * już przy tworzeniu reguły alertowej.
     */
    private boolean compare(double value, String operator, double threshold) {
        return switch (operator) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> Double.compare(value, threshold) == 0;
            case "!=" -> Double.compare(value, threshold) != 0;

            // Fallback dla nieznanego operatora.
            default -> value > threshold;
        };
    }
}