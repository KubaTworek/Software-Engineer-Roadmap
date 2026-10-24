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
        verify(query, Map.of());
    }

    public void verify(String query, Map<String,Object> variables) {
        Document document = new Parser().parseDocument(query);
        Map<String, FragmentDefinition> fragments = new HashMap<>();
        document.getDefinitionsOfType(FragmentDefinition.class).forEach(f -> fragments.put(f.getName(), f));
        for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
            var resolved = new HashMap<String,Object>();
            operation.getVariableDefinitions().forEach(definition -> {
                if (definition.getDefaultValue() instanceof IntValue integer)
                    resolved.put(definition.getName(), integer.getValue().intValueExact());
            });
            resolved.putAll(variables == null ? Map.of() : variables);
            int cost = cost(operation.getSelectionSet(), 1, 1, fragments, resolved);
            if (cost > MAX_COMPLEXITY) throw new IllegalArgumentException("GraphQL query complexity exceeded: " + cost);
        }
    }

    private int cost(SelectionSet selections, int depth, int multiplier, Map<String, FragmentDefinition> fragments,
                     Map<String,Object> variables) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("GraphQL query depth exceeded: " + depth);
        int result = 0;
        for (Selection<?> selection : selections.getSelections()) {
            if (selection instanceof Field field) {
                int requested = field.getArguments().stream()
                        .filter(a -> a.getName().equals("first"))
                        .map(a -> pageSize(a.getValue(), variables))
                        .findFirst().orElse(1);
                int childMultiplier = Math.multiplyExact(multiplier, Math.max(1, requested));
                result = Math.addExact(result, childMultiplier);
                if (field.getSelectionSet() != null) result = Math.addExact(result,
                        cost(field.getSelectionSet(), depth + 1, childMultiplier, fragments, variables));
            } else if (selection instanceof InlineFragment inline) {
                result = Math.addExact(result, cost(inline.getSelectionSet(), depth + 1, multiplier, fragments, variables));
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition fragment = fragments.get(spread.getName());
                if (fragment == null) throw new IllegalArgumentException("Unknown GraphQL fragment: " + spread.getName());
                result = Math.addExact(result, cost(fragment.getSelectionSet(), depth + 1, multiplier, fragments, variables));
            }
            if (result > MAX_COMPLEXITY) throw new IllegalArgumentException("GraphQL query complexity exceeded");
        }
        return result;
    }
    private int pageSize(Value<?> value, Map<String,Object> variables) {
        Object size = value instanceof VariableReference ref ? variables.get(ref.getName()) : value;
        int count;
        if (size instanceof IntValue integer) count = integer.getValue().intValueExact();
        else if (size instanceof Integer integer) count = integer;
        else throw new IllegalArgumentException("GraphQL first requires an integer");
        if (count < 0 || count > MAX_COMPLEXITY) throw new IllegalArgumentException("GraphQL page limit exceeded");
        return count;
    }
}
