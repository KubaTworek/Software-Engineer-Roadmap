package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.idempotency;

// Repository for processed message IDs.
// A real implementation should enforce a unique constraint on consumerName + messageId.
public interface ProcessedMessageRepository {

    boolean alreadyProcessed(String consumerName, String messageId);

    void markAsProcessed(ProcessedMessage processedMessage);
}