package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.ProductQueryUseCase;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.ProductSnapshot;

/** GraphQL is an input adapter; the use case has no dependency on GraphQL Java or Spring. */
@Controller
public final class ProductGraphQlController {

    private final ProductQueryUseCase productQuery;

    public ProductGraphQlController(ProductQueryUseCase productQuery) {
        this.productQuery = productQuery;
    }

    @QueryMapping
    public ProductSnapshot product(@Argument String id) {
        return productQuery.find(id);
    }
}
