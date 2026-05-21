package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.ConsumedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.deduplication.ProcessedEventRepository;

/**
 * Default implementation of idempotent event processing.
 *
 * This class protects the business handler from duplicate event delivery.
 */
public class DefaultIdempotentEventProcessor<T extends ConsumedEvent>
        implements IdempotentEventProcessor<T> {

    private final ProcessedEventRepository processedEventRepository;
    private final EventHandler<T> eventHandler;

    public DefaultIdempotentEventProcessor(
            ProcessedEventRepository processedEventRepository,
            EventHandler<T> eventHandler
    ) {
        this.processedEventRepository = processedEventRepository;
        this.eventHandler = eventHandler;
    }

    /**
     * Marks the event as processed and then executes business logic.
     *
     * In a production system, marking the event and applying business effects
     * should ideally happen in one database transaction.
     */
    @Override
    public ProcessingResult process(T event) {
        boolean isNewEvent = processedEventRepository.tryMarkAsProcessed(
                event.metadata().eventId()
        );

        if (!isNewEvent) {
            return ProcessingResult.DUPLICATE_SKIPPED;
        }

        try {
            eventHandler.handle(event);
            return ProcessingResult.PROCESSED;
        } catch (RetryableProcessingException exception) {
            processedEventRepository.removeProcessedMarker(event.metadata().eventId());
            return ProcessingResult.RETRYABLE_FAILURE;
        } catch (NonRetryableProcessingException exception) {
            return ProcessingResult.NON_RETRYABLE_FAILURE;
        } catch (RuntimeException exception) {
            processedEventRepository.removeProcessedMarker(event.metadata().eventId());
            return ProcessingResult.RETRYABLE_FAILURE;
        }
    }
}