package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay;

/**
 * Simplified Kafka replay service.
 *
 * A real implementation would use Kafka AdminClient or command-line tooling
 * to reset offsets for a specific consumer group.
 */
public class KafkaReplayService implements ReplayService {

    /**
     * Resets offsets according to the selected replay mode.
     */
    @Override
    public void resetOffsets(ReplayRequest request) {
        switch (request.mode()) {
            case FROM_BEGINNING ->
                    System.out.println("Resetting topic " + request.topic() + " to beginning.");

            case FROM_TIMESTAMP ->
                    System.out.println("Resetting topic "
                            + request.topic()
                            + " to timestamp "
                            + request.fromTimestamp());

            case FROM_CURRENT_OFFSET ->
                    System.out.println("Keeping current offsets for topic " + request.topic() + ".");
        }
    }
}