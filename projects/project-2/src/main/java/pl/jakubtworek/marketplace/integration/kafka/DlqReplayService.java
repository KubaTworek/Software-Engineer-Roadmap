package pl.jakubtworek.marketplace.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;

import java.util.UUID;

@Service
public class DlqReplayService {
    private final DlqEventRepository dlqRepository;
    private final ApplicationEventBus eventBus;
    private final OutboxEventMapper outboxEventMapper;
    private final KafkaEnvelopeMapper envelopeMapper = new KafkaEnvelopeMapper();

    public DlqReplayService(DlqEventRepository dlqRepository, ApplicationEventBus eventBus, ObjectMapper objectMapper) {
        this.dlqRepository = dlqRepository;
        this.eventBus = eventBus;
        this.outboxEventMapper = new OutboxEventMapper(objectMapper);
    }

    public void replay(UUID dlqEventId) {
        var dlqEvent = dlqRepository.findById(dlqEventId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ event not found: " + dlqEventId));
        try {
            var domainEvent = outboxEventMapper.toDomainEvent(envelopeMapper.toOutboxEvent(dlqEvent.envelope()));
            eventBus.publish(domainEvent);
            dlqRepository.save(dlqEvent.markReplayed());
        } catch (Exception e) {
            dlqRepository.save(dlqEvent.markReplayFailed(e.getMessage()));
            throw e;
        }
    }
}
