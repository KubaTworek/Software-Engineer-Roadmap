package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.replay;

/**
 * Defines supported replay strategies.
 */
public enum ReplayMode {

    /**
     * Replay all retained messages from the beginning of the topic.
     */
    FROM_BEGINNING,

    /**
     * Replay messages starting from a specific timestamp.
     */
    FROM_TIMESTAMP,

    /**
     * Replay only from the currently committed consumer group offset.
     */
    FROM_CURRENT_OFFSET
}