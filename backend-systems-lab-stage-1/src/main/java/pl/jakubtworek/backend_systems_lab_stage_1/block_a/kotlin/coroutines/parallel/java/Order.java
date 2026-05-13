package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.coroutines.parallel.java;

// Domain model representing an order.
public class Order {

    private final String id;
    private final double amount;

    public Order(String id, double amount) {
        this.id = id;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }
}