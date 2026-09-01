package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import java.util.List;

/** Rejects a query before resolvers execute and consume downstream resources. */
public final class QueryComplexityGuard {

    private final int maximumDepth;
    private final int maximumCost;

    public QueryComplexityGuard(int maximumDepth, int maximumCost) {
        if (maximumDepth < 1 || maximumCost < 1) {
            throw new IllegalArgumentException("limits must be positive");
        }
        this.maximumDepth = maximumDepth;
        this.maximumCost = maximumCost;
    }

    public QueryComplexity inspect(List<QueryField> roots) {
        QueryComplexity complexity;
        try {
            complexity = measure(roots, 1);
        } catch (ArithmeticException exception) {
            throw new QueryRejectedException("query cost exceeds supported range");
        }
        if (complexity.depth() > maximumDepth) {
            throw new QueryRejectedException("query depth " + complexity.depth() + " exceeds " + maximumDepth);
        }
        if (complexity.cost() > maximumCost) {
            throw new QueryRejectedException("query cost " + complexity.cost() + " exceeds " + maximumCost);
        }
        return complexity;
    }

    private QueryComplexity measure(List<QueryField> fields, int depth) {
        int cost = 0;
        int deepest = fields.isEmpty() ? depth - 1 : depth;
        for (QueryField field : fields) {
            cost = Math.addExact(cost, field.cost());
            QueryComplexity nested = measure(field.children(), depth + 1);
            cost = Math.addExact(cost, Math.multiplyExact(field.expectedCardinality(), nested.cost()));
            deepest = Math.max(deepest, nested.depth());
        }
        return new QueryComplexity(cost, deepest);
    }

    public record QueryComplexity(int cost, int depth) {
    }
}
