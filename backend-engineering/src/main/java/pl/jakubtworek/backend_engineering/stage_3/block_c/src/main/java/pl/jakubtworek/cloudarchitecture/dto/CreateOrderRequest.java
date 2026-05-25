package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.dto;

import java.util.List;

/**
 * Request body for order creation.
 *
 * This object should contain only client-provided input and no server-side state.
 */
public record CreateOrderRequest(String customerId, List<Long> productIds) {}
