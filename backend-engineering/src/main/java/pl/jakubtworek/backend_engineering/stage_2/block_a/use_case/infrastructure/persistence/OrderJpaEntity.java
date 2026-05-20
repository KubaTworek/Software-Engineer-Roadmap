package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.persistence;

// Persistence model used by the database adapter.
// It is not the same thing as the domain aggregate.
public final class OrderJpaEntity {

    private String id;
    private String customerId;
    private String status;
    private String currency;
    private String totalAmount;

    public OrderJpaEntity(
            String id,
            String customerId,
            String status,
            String currency,
            String totalAmount
    ) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.currency = currency;
        this.totalAmount = totalAmount;
    }

    public String id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String status() {
        return status;
    }

    public String currency() {
        return currency;
    }

    public String totalAmount() {
        return totalAmount;
    }
}