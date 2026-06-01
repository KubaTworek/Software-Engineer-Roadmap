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
import pl.jakubtworek.marketplace.integration.outbox.OutboxWorker;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/outbox")
public class OutboxAdminController {
    private final OutboxEventRepository repository;
    private final OutboxWorker worker;

    public OutboxAdminController(OutboxEventRepository repository, OutboxWorker worker) {
        this.repository = repository;
        this.worker = worker;
    }

    @GetMapping
    public List<OutboxEvent> list(
            @RequestParam(required = false) OutboxEventStatus status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return status == null ? repository.findAll(limit) : repository.findByStatus(status, limit);
    }

    @PostMapping("/{eventId}/publish")
    public void publish(@PathVariable UUID eventId) {
        worker.publishById(eventId);
    }

    @PostMapping("/{eventId}/retry")
    public void retry(@PathVariable UUID eventId) {
        worker.retryManually(eventId);
    }
}
