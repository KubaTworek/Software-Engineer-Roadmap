package com.example.observability.server.notification;

import com.example.observability.server.alert.AlertEvent;
import com.example.observability.server.alert.AlertRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Kanał powiadomień alertowych wysyłający alerty na webhook HTTP.
 *
 * Ten komponent jest jedną z implementacji NotificationChannel.
 * AlertRouter wybiera go wtedy, gdy route w regule alertowej ma type="webhook".
 *
 * Przykład route w regule:
 * {
 *   "type": "webhook",
 *   "target": "https://example.com/alerts"
 * }
 *
 * Rola tej klasy:
 * - zbudować payload JSON z AlertRule i AlertEvent,
 * - wysłać go metodą POST na target URL,
 * - nie blokować głównego alert evaluatora na czas odpowiedzi webhooka.
 */
@Component
public class WebhookNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationChannel.class);

    /**
     * Mapper używany do serializacji payloadu webhooka do JSON.
     *
     * Payload zawiera tylko najważniejsze dane alertu,
     * a nie cały obiekt AlertRule i AlertEvent.
     */
    private final ObjectMapper objectMapper;

    /**
     * Klient HTTP używany do wysyłania webhooków.
     *
     * Jest tworzony raz i współdzielony przez wszystkie wysyłki.
     * Wysyłka w notify() jest asynchroniczna przez sendAsync().
     */
    private final HttpClient client = HttpClient.newHttpClient();

    public WebhookNotificationChannel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Typ kanału używany przez AlertRouter.
     *
     * Jeśli AlertRule.AlertRoute ma type="webhook",
     * AlertRouter wybierze właśnie tę implementację.
     */
    @Override
    public String type() {
        return "webhook";
    }

    /**
     * Wysyła alert do zewnętrznego webhooka.
     *
     * Parametry:
     * - rule: definicja alertu, np. nazwa i routing,
     * - event: konkretne wystąpienie alertu, np. FIRING,
     * - target: URL webhooka.
     *
     * Przepływ:
     * 1. Buduje mały JSON payload z najważniejszymi polami alertu.
     * 2. Tworzy HTTP POST request na target.
     * 3. Ustawia Content-Type: application/json.
     * 4. Ustawia timeout 5 sekund.
     * 5. Wysyła request asynchronicznie.
     * 6. Loguje status odpowiedzi.
     *
     * sendAsync() jest istotne:
     * evaluator alertów nie czeka synchronicznie na webhook,
     * więc wolny endpoint zewnętrzny nie blokuje całego alertingu.
     */
    @Override
    public void notify(AlertRule rule, AlertEvent event, String target) {
        try {
            /*
             * Minimalny payload webhooka.
             *
             * Celowo nie wysyłamy całych obiektów domenowych,
             * żeby payload był stabilny i prosty dla odbiorcy.
             */
            String body = objectMapper.writeValueAsString(Map.of(
                    "rule", rule.getName(),
                    "tenantId", event.tenantId(),
                    "status", event.status(),
                    "observed", event.observedValue(),
                    "threshold", event.threshold(),
                    "message", event.message()
            ));

            /*
             * target pochodzi z konfiguracji reguły alertowej.
             *
             * Produkcyjnie trzeba go walidować przy tworzeniu reguły,
             * bo niekontrolowany webhook URL może prowadzić do SSRF.
             */
            HttpRequest req = HttpRequest.newBuilder(URI.create(target))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            /*
             * Asynchroniczna wysyłka.
             *
             * thenAccept loguje sam status HTTP, ale nie traktuje np. 500
             * jako wyjątku. Produkcyjnie warto uznać 4xx/5xx za failure.
             */
            client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(resp -> log.info(
                            "Alert webhook {} -> {}",
                            target,
                            resp.statusCode()
                    ));

        } catch (Exception e) {
            /*
             * Błędy budowy JSON-a, niepoprawny URI albo inne błędy lokalne
             * nie mogą wywalić AlertEvaluatora.
             *
             * Alert zostanie zapisany jako event, ale powiadomienie webhookiem
             * może nie zostać dostarczone.
             */
            log.warn("Webhook notification failed for {}", target, e);
        }
    }
}