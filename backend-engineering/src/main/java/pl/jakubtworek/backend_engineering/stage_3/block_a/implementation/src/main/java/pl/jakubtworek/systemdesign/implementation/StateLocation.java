package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Describes where application state is stored.
 *
 * A horizontally scalable API should not keep critical state only in process memory.
 * Critical state should be externalized to a durable or shared backing service.
 */
public enum StateLocation {
    PROCESS_MEMORY,
    LOCAL_EPHEMERAL_DISK,
    DATABASE,
    REDIS,
    OBJECT_STORAGE,
    MESSAGE_QUEUE,
    EXTERNAL_IDENTITY_PROVIDER
}
