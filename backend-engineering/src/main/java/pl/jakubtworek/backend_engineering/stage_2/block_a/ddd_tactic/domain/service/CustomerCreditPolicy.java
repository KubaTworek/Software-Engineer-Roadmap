package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.service;

import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.CustomerId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_tactic.domain.model.Money;

// Domain service for customer credit verification.
// It represents domain logic that may depend on several concepts.
public interface CustomerCreditPolicy {

    boolean canPlaceOrder(CustomerId customerId, Money totalAmount);
}