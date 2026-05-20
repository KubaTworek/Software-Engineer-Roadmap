package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.architecture.contextmap;

// Defines supported communication styles between bounded contexts.
public enum ChannelType {
    REST_API,
    GRPC_API,
    DOMAIN_EVENT,
    INTEGRATION_EVENT,
    MESSAGE_BROKER
}