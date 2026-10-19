package pl.jakubtworek.marketplace.integration.kafka.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.integration.kafka.*;
import pl.jakubtworek.marketplace.security.SecurityAuditLog;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * Administracyjny kontroler dla mechanizmów związanych z Kafką.
 *
 * Ten kontroler nie jest publicznym API biznesowym.
 * Służy do diagnostyki i operacyjnej obsługi zdarzeń, które nie zostały poprawnie
 * przetworzone przez konsumentów.
 *
 * Główne odpowiedzialności:
 * - podgląd eventów znajdujących się w DLQ,
 * - filtrowanie eventów DLQ po statusie,
 * - ręczne uruchomienie replay konkretnego eventu z DLQ.
 *
 * W prawdziwym systemie endpointy tego typu powinny być zabezpieczone:
 * - autoryzacją,
 * - audytem,
 * - ograniczeniem dostępu do operatorów/administratorów,
 * - limitem zapytań,
 * - ostrożnym maskowaniem danych w payloadach.
 */
@RestController
@RequestMapping("/admin/kafka")
public class KafkaAdminController {

    /**
     * Repozytorium eventów DLQ.
     *
     * DLQ, czyli Dead Letter Queue, przechowuje eventy, których nie udało się poprawnie
     * przetworzyć po określonej liczbie prób.
     *
     * Przykładowe powody trafienia do DLQ:
     * - nieobsługiwana wersja eventu,
     * - niepoprawny payload,
     * - błąd deserializacji,
     * - trwały błąd biznesowy,
     * - błąd handlera, którego retry nie rozwiązał.
     */
    private final DlqEventRepository dlqRepository;

    /**
     * Serwis odpowiedzialny za replay eventów z DLQ.
     *
     * Replay oznacza próbę ponownego przetworzenia eventu, zwykle po naprawieniu przyczyny
     * błędu, np. po poprawieniu kodu konsumenta albo danych.
     */
    private final DlqReplayService replayService;
    private final SecurityAuditLog audit;

    public KafkaAdminController(
            DlqEventRepository dlqRepository,
            DlqReplayService replayService,
            SecurityAuditLog audit
    ) {
        this.dlqRepository = dlqRepository;
        this.replayService = replayService;
        this.audit = audit;
    }

    /**
     * Zwraca eventy znajdujące się w DLQ.
     *
     * Jeśli status nie zostanie podany, endpoint zwraca wszystkie eventy.
     * Jeśli status zostanie podany, endpoint zwraca eventy tylko o danym statusie.
     *
     * Przykłady:
     * GET /admin/kafka/dlq
     * GET /admin/kafka/dlq?status=NEW
     * GET /admin/kafka/dlq?status=REPLAYED
     * GET /admin/kafka/dlq?status=FAILED
     *
     * Obecnie przy filtrowaniu po statusie limit jest ustawiony na sztywno na 100.
     * Dla większej kontroli operacyjnej warto dodać parametr limit.
     */
    @GetMapping("/dlq")
    public ResponseEntity<?> dlq(@RequestParam(required = false) DlqEventStatus status) {
        if (status == null) {
            return ResponseEntity.ok(dlqRepository.findAll().stream().map(KafkaAdminController::summary).toList());
        }

        return ResponseEntity.ok(dlqRepository.findByStatus(status, 100).stream().map(KafkaAdminController::summary).toList());
    }

    /**
     * Uruchamia replay konkretnego eventu z DLQ.
     *
     * Replay powinien:
     * - pobrać event z DLQ,
     * - opublikować go ponownie do odpowiedniego topicu albo dispatchera,
     * - zmienić status eventu DLQ, np. na REPLAYED,
     * - zachować informację diagnostyczną, jeśli replay się nie uda.
     *
     * Zwracamy HTTP 202 Accepted, ponieważ replay jest operacją techniczno-operacyjną.
     * W przyszłości może być wykonywany asynchronicznie.
     */
    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<Void> replay(@PathVariable UUID id, Authentication authentication) {
        replayService.replay(id);
        audit.dlqReplay(authentication == null ? "local-unsecured" : authentication.getName(), id);

        return ResponseEntity.accepted().build();
    }

    private static java.util.Map<String, Object> summary(DlqEvent event) {
        return java.util.Map.of(
                "id", event.id(), "eventId", event.envelope().eventId(),
                "eventType", event.envelope().eventType(), "status", event.status(),
                "attempts", event.attempts(), "failedAt", event.failedAt());
    }
}
