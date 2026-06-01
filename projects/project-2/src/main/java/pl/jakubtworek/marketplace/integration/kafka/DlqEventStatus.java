package pl.jakubtworek.marketplace.integration.kafka;

public enum DlqEventStatus {
    NEW,
    REPLAYED,
    REPLAY_FAILED
}
