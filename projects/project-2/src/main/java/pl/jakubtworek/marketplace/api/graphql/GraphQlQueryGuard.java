package pl.jakubtworek.marketplace.api.graphql;

import graphql.language.*;
import graphql.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Liczy rzeczywisty AST, a nie nazwę resolvera. Alias nie obniża kosztu, listy
 * mnożą koszt potomków, a fragmenty są rozwijane. W ten sposób jeden endpoint
 * GraphQL nie staje się nieograniczonym językiem zapytań do backendu.
 */
@Component
public class GraphQlQueryGuard {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_COMPLEXITY = 100;

    public void verify(String query) {
        Document document = new Parser().parseDocument(query);
        Map<String, FragmentDefinition> fragments = new HashMap<>();
        document.getDefinitionsOfType(FragmentDefinition.class).forEach(f -> fragments.put(f.getName(), f));
        for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
            int cost = cost(operation.getSelectionSet(), 1, 1, fragments);
            if (cost > MAX_COMPLEXITY) throw new IllegalArgumentException("GraphQL query complexity exceeded: " + cost);
        }
    }

    private int cost(SelectionSet selections, int depth, int multiplier, Map<String, FragmentDefinition> fragments) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("GraphQL query depth exceeded: " + depth);
        int result = 0;
        for (Selection<?> selection : selections.getSelections()) {
            if (selection instanceof Field field) {
                int requested = field.getArguments().stream()
                        .filter(a -> a.getName().equals("first") && a.getValue() instanceof IntValue)
                        .map(a -> ((IntValue) a.getValue()).getValue().intValueExact())
                        .findFirst().orElse(1);
                int childMultiplier = Math.multiplyExact(multiplier, Math.max(1, requested));
                result = Math.addExact(result, childMultiplier);
                if (field.getSelectionSet() != null) result = Math.addExact(result,
                        cost(field.getSelectionSet(), depth + 1, childMultiplier, fragments));
            } else if (selection instanceof InlineFragment inline) {
                result = Math.addExact(result, cost(inline.getSelectionSet(), depth + 1, multiplier, fragments));
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition fragment = fragments.get(spread.getName());
                if (fragment == null) throw new IllegalArgumentException("Unknown GraphQL fragment: " + spread.getName());
                result = Math.addExact(result, cost(fragment.getSelectionSet(), depth + 1, multiplier, fragments));
            }
        }
        return result;
    }
}
