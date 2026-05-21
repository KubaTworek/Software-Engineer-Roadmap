package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay;

/**
 * Service responsible for resetting consumer offsets before replay.
 *
 * In production, this operation should be protected because replay can trigger
 * large amounts of processing and side effects.
 */
public interface ReplayService {

    /**
     * Prepares the consumer group for replay according to the request.
     */
    void resetOffsets(ReplayRequest request);
}