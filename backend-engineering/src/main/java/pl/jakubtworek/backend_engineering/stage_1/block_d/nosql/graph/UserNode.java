package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.graph;

import java.time.Instant;
import java.util.Set;

/**
 * Przykład węzła użytkownika w graph DB, np. Neo4j.
 */
public class UserNode {

    private final String id;
    private final String email;
    private final String displayName;
    private final Set<String> labels;
    private final Instant createdAt;

    public UserNode(
            String id,
            String email,
            String displayName,
            Set<String> labels,
            Instant createdAt
    ) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.labels = Set.copyOf(labels);
        this.createdAt = createdAt;
    }

    public boolean hasLabel(String label) {
        return labels.contains(label);
    }

    public String id() { return id; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public Set<String> labels() { return labels; }
    public Instant createdAt() { return createdAt; }
}
