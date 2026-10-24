package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import org.springframework.context.annotation.*;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.*;

/** Spring Boot wykrywa Instrumentation; budżet działa przed wywołaniem portu aplikacji. */
@Configuration(proxyBeanMethods = false)
public class GraphQlRuntimePolicy {
    public GraphQlRuntimePolicy(BatchLoaderRegistry registry, ProductQueryUseCase query) {
        registry.<String,ProductSnapshot>forName("productById").registerMappedBatchLoader((ids, env) ->
                reactor.core.publisher.Mono.fromSupplier(() -> {
                    var result = new java.util.HashMap<String,ProductSnapshot>();
                    ids.forEach(id -> result.put(id, query.find(id)));
                    return result;
                }));
        // Registry tworzy DataLoader i cache per request. Nie współdzielimy cache użytkowników.
        // Port wykonuje tu odczyt per unikalny id; prawdziwe SQL IN należy dodać na granicy adaptera DB.
    }
    @Bean graphql.analysis.MaxQueryDepthInstrumentation queryDepth() {
        return new graphql.analysis.MaxQueryDepthInstrumentation(5);
    }
    @Bean ExpandedOperationBudget expandedOperationBudget() { return new ExpandedOperationBudget(); }
    @Bean graphql.analysis.MaxQueryComplexityInstrumentation queryCost() {
        return new graphql.analysis.MaxQueryComplexityInstrumentation(100, (env, children) -> {
            Object ids = env.getArguments().get("ids");
            int cardinality = ids instanceof java.util.List<?> list ? list.size() : 1;
            if (cardinality > 20) return 101;
            return Math.addExact(1, Math.multiplyExact(cardinality, children));
        });
    }
}
