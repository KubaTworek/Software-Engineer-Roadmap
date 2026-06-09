package pl.jakubtworek.chatsystem.production;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.chatsystem.outbox.InMemoryQueueEventBus;
import pl.jakubtworek.chatsystem.outbox.OutboxEventRepository;
import pl.jakubtworek.chatsystem.outbox.OutboxStatus;

@RestController
@RequestMapping("/api/admin/production")
public class ProductionController {
    private final OutboxEventRepository outboxRepository;
    private final InMemoryQueueEventBus queue;

    public ProductionController(OutboxEventRepository outboxRepository, InMemoryQueueEventBus queue) {
        this.outboxRepository = outboxRepository;
        this.queue = queue;
    }

    @GetMapping("/status")
    public ProductionStatusResponse status() {
        return new ProductionStatusResponse(
                outboxRepository.countByStatus(OutboxStatus.NEW),
                outboxRepository.countByStatus(OutboxStatus.FAILED),
                queue.size(),
                "JpaMessageStore; replace with Scylla/Cassandra/DynamoDB adapter for very high write volume",
                "Database outbox + local queue; replace EventBus with Kafka/NATS/RabbitMQ adapter in multi-node production"
        );
    }
}
