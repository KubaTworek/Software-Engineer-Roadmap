package com.example.observability.server.notification;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Router powiadomień alertowych.
 *
 * AlertEvaluator odpowiada za wykrycie, że reguła alertowa fire'uje.
 * AlertRouter odpowiada za dostarczenie tego alertu do właściwych kanałów.
 *
 * Dzięki tej separacji:
 * - evaluator nie zna szczegółów Slacka, webhooków, maili ani logowania,
 * - dodanie nowego kanału wymaga nowej implementacji NotificationChannel,
 * - routing jest sterowany konfiguracją reguły alertowej.
 */
@Service
public class AlertRouter {

    /**
     * Mapa dostępnych kanałów powiadomień.
     *
     * Klucz:
     * - typ kanału, np. "log", "webhook", "slack", "email".
     *
     * Wartość:
     * - konkretna implementacja NotificationChannel.
     *
     * Mapa jest budowana automatycznie ze wszystkich beanów Springa
     * implementujących NotificationChannel.
     */
    private final Map<String, NotificationChannel> channels;

    /**
     * Konstruktor dostaje wszystkie dostępne kanały powiadomień z kontenera Springa.
     *
     * Przykład:
     * - LogNotificationChannel
     * - WebhookNotificationChannel
     * - SlackNotificationChannel
     *
     * Każdy kanał deklaruje swój typ przez NotificationChannel.type().
     */
    public AlertRouter(List<NotificationChannel> channels) {
        this.channels = channels
                .stream()
                .collect(Collectors.toMap(
                        NotificationChannel::type,
                        Function.identity()
                ));
    }

    /**
     * Kieruje alert do kanałów zdefiniowanych w regule.
     *
     * Przepływ:
     * 1. Jeśli reguła nie ma routes, używany jest domyślny kanał "log".
     * 2. Jeśli routes istnieją, każda route wybiera kanał po type.
     * 3. Jeśli kanał istnieje, dostaje rule, event i target.
     * 4. Jeśli kanału nie ma, route jest ignorowana.
     *
     * rule:
     * - definicja alertu, m.in. nazwa, severity, routes.
     *
     * event:
     * - konkretne wystąpienie alertu, np. FIRING.
     *
     * target:
     * - miejsce dostarczenia, np. URL webhooka, nazwa kanału Slack,
     *   adres e-mail albo "default" dla logowania.
     */
    public void route(AlertRule rule, AlertEvent event) {

        /*
         * Brak routes oznacza fallback do kanału log.
         *
         * To jest ważne dla MVP:
         * nawet jeśli użytkownik nie skonfiguruje webhooka/slacka,
         * alert nie znika całkowicie — zostanie zalogowany.
         *
         * Uwaga produkcyjna:
         * channels.get("log") może zwrócić null, jeśli nie ma kanału "log".
         * Warto dodać walidację przy starcie aplikacji albo null-check.
         */
        if (rule.getRoutes() == null || rule.getRoutes().isEmpty()) {
            channels.get("log").notify(rule, event, "default");
            return;
        }

        /*
         * Jedna reguła może mieć wiele tras.
         *
         * Przykład:
         * - critical alert -> PagerDuty + Slack,
         * - warning alert -> Slack,
         * - audit alert -> webhook.
         */
        for (AlertRule.AlertRoute route : rule.getRoutes()) {
            NotificationChannel channel = channels.get(route.getType());

            /*
             * Jeśli kanał o podanym typie istnieje, wysyłamy powiadomienie.
             *
             * Jeśli nie istnieje, route jest pomijana.
             * To jest tolerancyjne, ale może ukrywać błędy konfiguracji.
             */
            if (channel != null) {
                channel.notify(rule, event, route.getTarget());
            }
        }
    }
}