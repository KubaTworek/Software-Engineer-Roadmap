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

    public ProductSnapshot product(@Argument String id) {
        return productQuery.find(id);
    }

    @QueryMapping(name = "product")
    public java.util.concurrent.CompletableFuture<ProductSnapshot> loadProduct(@Argument String id,
            graphql.schema.DataFetchingEnvironment environment) {
        return environment.<String,ProductSnapshot>getDataLoader("productById").load(id);
    }

    @QueryMapping
    public java.util.concurrent.CompletableFuture<java.util.List<ProductSnapshot>> products(@Argument java.util.List<String> ids,
            graphql.schema.DataFetchingEnvironment environment) {
        return environment.<String,ProductSnapshot>getDataLoader("productById").loadMany(ids);
    }

    @org.springframework.graphql.data.method.annotation.SchemaMapping(typeName = "Product")
    public int internalMarginPercent(ProductSnapshot product, java.security.Principal principal) {
        var roles = principal instanceof org.springframework.security.core.Authentication auth
                ? auth.getAuthorities().stream().map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                    .collect(java.util.stream.Collectors.toSet()) : java.util.Set.<String>of();
        return new ProductFieldAuthorization().internalMargin(
                new ProductView(product.id(), product.ownerId(), product.name(), product.internalMarginPercent()),
                new ProductFieldAuthorization.Principal(principal == null ? "" : principal.getName(), roles));
    }
}
