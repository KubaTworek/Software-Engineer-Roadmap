package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.architecture.contextmap;

import java.util.List;

// Defines a context map for the whole system.
// It documents bounded contexts and their relationships.
public final class ContextMap {

    private final List<BoundedContext> contexts;
    private final List<ContextRelationship> relationships;

    public ContextMap(
            List<BoundedContext> contexts,
            List<ContextRelationship> relationships
    ) {
        this.contexts = List.copyOf(contexts);
        this.relationships = List.copyOf(relationships);
    }

    public List<BoundedContext> contexts() {
        return contexts;
    }

    public List<ContextRelationship> relationships() {
        return relationships;
    }
}