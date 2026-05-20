package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Money;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.ProductId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Quantity;

// Domain service for pricing logic that does not naturally belong to one entity.
// It remains independent from infrastructure and frameworks.
public interface PricingPolicy {

    Money calculatePrice(ProductId productId, Quantity quantity);
}