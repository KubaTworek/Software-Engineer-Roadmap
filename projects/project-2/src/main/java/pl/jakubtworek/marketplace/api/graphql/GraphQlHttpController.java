package pl.jakubtworek.marketplace.api.graphql;

import graphql.ExecutionInput;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Jawny adapter HTTP dla Spring Boot 4. Sam silnik, schema i resolvery pochodzą ze
 * Spring GraphQL; kontroler jedynie mapuje transport JSON na ExecutionInput.
 * Dzięki temu test nie zadowala się istnieniem pliku schema, lecz wykonuje query.
 */
@RestController
public class GraphQlHttpController {
    private final GraphQlSource source;
    private final GraphQlQueryGuard guard;
    public GraphQlHttpController(GraphQlSource source, GraphQlQueryGuard guard) {
        this.source = source; this.guard = guard;
    }

    @PostMapping(path = "/graphql", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Map<String, Object>> execute(@RequestBody Request request) {
        guard.verify(request.query(), request.variables());
        ExecutionInput input = ExecutionInput.newExecutionInput(request.query())
                .operationName(request.operationName())
                .variables(request.variables() == null ? Map.of() : request.variables())
                .build();
        return source.graphQl().executeAsync(input).thenApply(result -> result.toSpecification());
    }

    public record Request(String query, String operationName, Map<String, Object> variables) {}
}
