package pl.jakubtworek.backend_engineering.stage_2.block_b.kafka.ordering;

/**
 * Represents the ordering guarantee available in Kafka.
 *
 * Kafka guarantees ordering only within a single partition.
 */
public class OrderingGuarantee {

    /**
     * Checks whether two events can be ordered by Kafka.
     *
     * Events can be ordered by Kafka only if they are written to the same topic
     * and the same partition.
     */
    public boolean isOrderingGuaranteed(
            String firstTopic,
            int firstPartition,
            String secondTopic,
            int secondPartition
    ) {
        return firstTopic.equals(secondTopic)
                && firstPartition == secondPartition;
    }
}