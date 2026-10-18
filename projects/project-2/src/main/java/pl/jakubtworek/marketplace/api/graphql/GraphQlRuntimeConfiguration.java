package pl.jakubtworek.marketplace.api.graphql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.execution.GraphQlSource;
import java.util.UUID;

/** Buduje wykonywalną schemę jawnie, aby transport nie zależał od ukrytej auto-konfiguracji. */
@Configuration
public class GraphQlRuntimeConfiguration {
    @Bean
    GraphQlSource graphQlSource(OrderGraphQlController queries) {
        return GraphQlSource.schemaResourceBuilder()
                .schemaResources(new ClassPathResource("graphql/schema.graphqls"))
                .configureRuntimeWiring(wiring -> wiring.type("Query", type -> type
                        .dataFetcher("order", environment ->
                                queries.order(UUID.fromString(environment.getArgument("id"))))))
                .build();
    }
}
