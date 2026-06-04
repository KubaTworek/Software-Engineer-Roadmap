package pl.jakubtworek.marketplace.integration.outbox.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;
import pl.jakubtworek.marketplace.integration.kafka.KafkaOutboxWorker;

import java.util.List;
import java.util.UUID;

/**
 * Administracyjny kontroler do obsługi outboxa.
 *
 * Ten kontroler nie jest częścią publicznego API biznesowego.
 * Służy do diagnostyki i ręcznego zarządzania eventami zapisanymi w outboxie.
 *
 * Przykładowe zastosowania:
 * - sprawdzenie, jakie eventy czekają na publikację,
 * - podejrzenie eventów zakończonych błędem,
 * - ręczna publikacja konkretnego eventu,
 * - ręczne ponowienie eventu po naprawieniu problemu.
 *
 * W prawdziwym systemie endpointy tego typu powinny być zabezpieczone:
 * - autoryzacją,
 * - ograniczeniem dostępu tylko dla administratorów/operatorów,
 * - audytem wywołań,
 * - ewentualnie osobnym panelem operacyjnym.
 */
@RestController
@RequestMapping("/admin/outbox")
public class OutboxAdminController {

    /**
     * Repozytorium outboxa.
     *
     * Używane do odczytu eventów zapisanych w outboxie.
     * Kontroler nie zna szczegółów implementacji repozytorium, np. czy dane są w pamięci,
     * czy w PostgreSQL.
     */
    private final OutboxEventRepository repository;

    /**
     * Worker publikujący eventy z outboxa do Kafki.
     *
     * W tej wersji endpoint administracyjny steruje workerem kafkowym bezpośrednio:
     * - może opublikować konkretny event,
     * - może wymusić retry konkretnego eventu.
     *
     * To oznacza, że po fazie 4 główną ścieżką publikacji jest KafkaOutboxWorker,
     * a nie lokalny OutboxWorker z fazy 3.
     */
    private final KafkaOutboxWorker worker;

    public OutboxAdminController(
            OutboxEventRepository repository,
            KafkaOutboxWorker worker
    ) {
        this.repository = repository;
        this.worker = worker;
    }

    /**
     * Zwraca listę eventów zapisanych w outboxie.
     *
     * Parametry:
     * - status: opcjonalny filtr po statusie eventu, np. NEW, PUBLISHED, FAILED,
     * - limit: maksymalna liczba zwracanych rekordów.
     *
     * Jeśli status nie zostanie podany, endpoint zwraca eventy niezależnie od statusu.
     *
     * Przykłady:
     * GET /admin/outbox
     * GET /admin/outbox?status=NEW
     * GET /admin/outbox?status=FAILED&limit=50
     */
    @GetMapping
    public List<OutboxEvent> list(
            @RequestParam(required = false) OutboxEventStatus status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return status == null
                ? repository.findAll(limit)
                : repository.findByStatus(status, limit);
    }

    /**
     * Publikuje konkretny event z outboxa po jego ID.
     *
     * Ten endpoint jest przydatny, gdy operator chce wymusić publikację pojedynczego eventu,
     * np. po ręcznej analizie problemu.
     *
     * Jeśli event został już opublikowany, worker powinien potraktować operację
     * idempotentnie i nie publikować go ponownie.
     */
    @PostMapping("/{eventId}/publish")
    public void publish(@PathVariable UUID eventId) {
        worker.publishById(eventId);
    }

    /**
     * Oznacza konkretny event do ponowienia i próbuje go ponownie opublikować.
     *
     * Ten endpoint jest użyteczny dla eventów, które wcześniej zakończyły się błędem,
     * np. przez chwilowy problem z brokerem, serializacją albo zależnością zewnętrzną.
     *
     * Różnica względem publish(...):
     * - retry(...) najpierw przywraca event do stanu możliwego do ponowienia,
     * - potem uruchamia publikację.
     */
    @PostMapping("/{eventId}/retry")
    public void retry(@PathVariable UUID eventId) {
        worker.retryManually(eventId);
    }
}