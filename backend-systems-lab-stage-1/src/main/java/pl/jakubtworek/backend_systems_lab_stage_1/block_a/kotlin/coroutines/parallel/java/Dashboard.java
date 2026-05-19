package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.java;

import java.util.List;

// Dashboard aggregates data from multiple services.
public class Dashboard {

    private final User user;
    private final List<Order> orders;

    public Dashboard(User user, List<Order> orders) {
        this.user = user;
        this.orders = orders;
    }

    public User getUser() {
        return user;
    }

    public List<Order> getOrders() {
        return orders;
    }
}