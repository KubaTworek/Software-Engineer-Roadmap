package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.adapter.out.persistance;

// Persistence entity.
// This object belongs to infrastructure and may use JPA annotations in a real implementation.
public final class OrderJpaEntity {

    private String id;
    private String customerId;
    private String status;

    public OrderJpaEntity() {
    }

    public OrderJpaEntity(String id, String customerId, String status) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
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
}