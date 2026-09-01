package pl.jakubtworek.backend_engineering.stage_2.block_a.graphql;

import java.util.Set;

/** Field-level authorization still checks the concrete resource, not only the operation name. */
public final class ProductFieldAuthorization {

    public int internalMargin(ProductView product, Principal principal) {
        boolean owner = product.ownerId().equals(principal.userId());
        boolean privileged = principal.roles().contains("FINANCE");
        if (!owner && !privileged) {
            throw new FieldAccessDeniedException("internalMargin is not available for this product");
        }
        return product.internalMarginPercent();
    }

    public record Principal(String userId, Set<String> roles) {
        public Principal {
            roles = Set.copyOf(roles);
        }
    }
}
