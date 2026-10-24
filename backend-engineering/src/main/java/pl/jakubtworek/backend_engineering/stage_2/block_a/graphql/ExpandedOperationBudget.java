package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import graphql.execution.instrumentation.*;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.*;
import java.util.Map;

/** Ostrożny koszt rozwiniętego AST: alias i ponowne użycie fragmentu zużywają budżet.
 * Działa po walidacji GraphQL i koercji zmiennych, przed jakimkolwiek resolverem. */
public final class ExpandedOperationBudget extends SimplePerformantInstrumentation {
    @Override public InstrumentationContext<graphql.ExecutionResult> beginExecuteOperation(
            InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        var context = parameters.getExecutionContext();
        cost(context.getOperationDefinition().getSelectionSet(), 1, context.getFragmentsByName(),
                context.getCoercedVariables().toMap(), new int[]{100});
        return null;
    }
    private int cost(SelectionSet selections, int depth, Map<String,FragmentDefinition> fragments,
                     Map<String,Object> variables, int[] remaining) {
        if (depth > 5) throw new graphql.execution.AbortExecutionException("GraphQL depth exceeded");
        int total = 0;
        for (Selection<?> selection : selections.getSelections()) {
            if (--remaining[0] < 0) throw new graphql.execution.AbortExecutionException("GraphQL work budget exceeded");
            int part = 0;
            if (selection instanceof Field field) {
                int cardinality = 1;
                for (Argument argument : field.getArguments()) if (argument.getName().equals("ids")) {
                    Value<?> value = argument.getValue();
                    Object ids = value instanceof VariableReference variable ? variables.get(variable.getName()) : value;
                    cardinality = ids instanceof java.util.List<?> list ? list.size()
                            : ids instanceof ArrayValue array ? array.getValues().size() : 1;
                    if (cardinality > 20) throw new graphql.execution.AbortExecutionException("GraphQL list limit exceeded");
                }
                int children = field.getSelectionSet() == null ? 0 : cost(field.getSelectionSet(), depth + 1, fragments, variables, remaining);
                part = 1 + cardinality * children;
            } else if (selection instanceof FragmentSpread spread) {
                part = cost(fragments.get(spread.getName()).getSelectionSet(), depth, fragments, variables, remaining);
            } else if (selection instanceof InlineFragment inline) {
                part = cost(inline.getSelectionSet(), depth, fragments, variables, remaining);
            }
            total += part;
            if (total > 100) throw new graphql.execution.AbortExecutionException("GraphQL cost exceeded");
        }
        return total;
    }
}
