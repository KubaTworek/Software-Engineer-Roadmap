package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import java.util.List;

public record QueryField(String name, int cost, int expectedCardinality, List<QueryField> children) {

    public QueryField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name is required");
        }
        if (cost < 1 || expectedCardinality < 1) {
            throw new IllegalArgumentException("field cost and cardinality must be positive");
        }
        children = List.copyOf(children);
    }

    public static QueryField leaf(String name, int cost) {
        return new QueryField(name, cost, 1, List.of());
    }

    public static QueryField node(String name, int cost, QueryField... children) {
        return new QueryField(name, cost, 1, List.of(children));
    }

    public static QueryField list(String name, int cost, int maximumItems, QueryField... children) {
        return new QueryField(name, cost, maximumItems, List.of(children));
    }
}
