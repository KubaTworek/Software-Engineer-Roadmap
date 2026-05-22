package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.architecture.contextmap;

// Describes a relationship between two bounded contexts.
// It defines who is upstream, who is downstream, and how they communicate.
public final class ContextRelationship {

    private final BoundedContext upstream;
    private final BoundedContext downstream;
    private final RelationshipPattern pattern;
    private final CommunicationChannel channel;

    public ContextRelationship(
            BoundedContext upstream,
            BoundedContext downstream,
            RelationshipPattern pattern,
            CommunicationChannel channel
    ) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.pattern = pattern;
        this.channel = channel;
    }

    public BoundedContext upstream() {
        return upstream;
    }

    public BoundedContext downstream() {
        return downstream;
    }

    public RelationshipPattern pattern() {
        return pattern;
    }

    public CommunicationChannel channel() {
        return channel;
    }
}