package pl.jakubtworek.marketplace.integration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

/**
 * Transactional publisher used by application services and event handlers.
 *
 * It deliberately does not call handlers directly. It stores the event in the outbox so the
 * outbox worker can publish it after the transaction that changed the aggregate has committed.
 */
@Primary
@Component
public class OutboxEventPublisher implements EventPublisher {
    private final OutboxEventRepository repository;
    private final OutboxEventMapper mapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this(repository, new OutboxEventMapper(objectMapper));
    }

    public OutboxEventPublisher(OutboxEventRepository repository, OutboxEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void publish(DomainEvent event) {
        repository.save(mapper.toOutboxEvent(event));
    }
}
