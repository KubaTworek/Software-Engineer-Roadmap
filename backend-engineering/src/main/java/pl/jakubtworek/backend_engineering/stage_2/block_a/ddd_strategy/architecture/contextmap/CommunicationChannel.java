package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.architecture.contextmap;

// Represents the technical communication mechanism between contexts.
public final class CommunicationChannel {

    private final String name;
    private final ChannelType type;

    public CommunicationChannel(String name, ChannelType type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public ChannelType type() {
        return type;
    }
}