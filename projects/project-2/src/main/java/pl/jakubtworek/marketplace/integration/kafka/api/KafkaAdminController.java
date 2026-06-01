package pl.jakubtworek.marketplace.integration.kafka.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.integration.kafka.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/kafka")
public class KafkaAdminController {
    private final DlqEventRepository dlqRepository;
    private final DlqReplayService replayService;

    public KafkaAdminController(DlqEventRepository dlqRepository, DlqReplayService replayService) {
        this.dlqRepository = dlqRepository;
        this.replayService = replayService;
    }

    @GetMapping("/dlq")
    public ResponseEntity<?> dlq(@RequestParam(required = false) DlqEventStatus status) {
        if (status == null) return ResponseEntity.ok(dlqRepository.findAll());
        return ResponseEntity.ok(dlqRepository.findByStatus(status, 100));
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<Void> replay(@PathVariable UUID id) {
        replayService.replay(id);
        return ResponseEntity.accepted().build();
    }
}
