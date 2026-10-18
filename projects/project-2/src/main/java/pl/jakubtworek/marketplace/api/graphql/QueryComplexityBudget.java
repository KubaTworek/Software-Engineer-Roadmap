package pl.jakubtworek.marketplace.api.graphql;

import java.util.List;

/**
 * GraphQL rozwiązuje problem elastycznego odczytu wielu powiązanych pól, ale koszt
 * requestu przestaje wynikać z samego URL. Budżet liczy głębokość, koszt pól oraz
 * mnożnik kolekcji. To semantyka niezależna od frameworka; adapter GraphQL powinien
 * policzyć ją przed resolverami, stosować DataLoader przeciw N+1 i autoryzować każde
 * pole, nie tylko endpoint /graphql.
 */
public final class QueryComplexityBudget {
    public record Field(int baseCost, int requestedItems, List<Field> children) {
        public Field { children = List.copyOf(children); }
    }

    private final int maximum;
    public QueryComplexityBudget(int maximum) { this.maximum = maximum; }

    public int verify(Field root) {
        int cost = cost(root, 1);
        if (cost > maximum) throw new IllegalArgumentException("GraphQL query complexity exceeded: " + cost);
        return cost;
    }

    private int cost(Field field, int parentMultiplier) {
        int multiplier = parentMultiplier * Math.max(1, field.requestedItems());
        return field.baseCost() * multiplier + field.children().stream().mapToInt(child -> cost(child, multiplier)).sum();
    }
}
