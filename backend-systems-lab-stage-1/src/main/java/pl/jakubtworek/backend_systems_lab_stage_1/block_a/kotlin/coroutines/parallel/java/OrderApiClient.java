package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.java;

import java.util.List;

// OrderApiClient simulates loading orders from another service.
public class OrderApiClient {

    public List<Order> fetchOrdersByUserId(String userId) {
        return List.of(
                new Order("ORD-1", 120.0),
                new Order("ORD-2", 75.5)
        );
    }
}