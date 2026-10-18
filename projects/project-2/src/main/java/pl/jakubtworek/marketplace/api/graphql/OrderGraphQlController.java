package pl.jakubtworek.marketplace.api.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;

import java.util.UUID;

/**
 * Prawdziwy adapter GraphQL, ale nadal cienka granica odczytu. Nie zwraca agregatu,
 * dzięki czemu schema publiczna nie staje się przypadkowym odbiciem domeny.
 * Limit kosztu jest sprawdzany przed pobraniem danych; rozbudowany system powinien
 * użyć instrumentacji analizującej całe AST i autoryzacji na poziomie pól.
 */
@Controller
public class OrderGraphQlController {
    private final OrderRepository orders;
    private final QueryComplexityBudget complexity = new QueryComplexityBudget(10);

    public OrderGraphQlController(OrderRepository orders) { this.orders = orders; }

    @QueryMapping
    @PreAuthorize("@orderOwnership.canRead(#id, authentication)")
    public OrderView order(@Argument UUID id) {
        complexity.verify(new QueryComplexityBudget.Field(1, 1, java.util.List.of()));
        return orders.findById(OrderId.of(id))
                .map(order -> new OrderView(order.id().value(), order.status().name(), order.version()))
                .orElse(null);
    }

    public record OrderView(UUID id, String status, long version) {}
}
