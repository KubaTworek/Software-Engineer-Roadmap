package pl.jakubtworek.backend_engineering.stage_2.block_a.clean_architecture.domain.model;

// Domain entity.
// This class must not import Spring, JPA, HTTP, Kafka, or any infrastructure framework.
public final class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private OrderStatus status;

    private Order(OrderId id, CustomerId customerId, OrderStatus status) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
    }

    public static Order create(OrderId id, CustomerId customerId) {
        return new Order(id, customerId, OrderStatus.DRAFT);
    }

    // Business behavior belongs to the domain entity.
    public void place() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Only draft order can be placed");
        }

        this.status = OrderStatus.PLACED;
    }

    public OrderId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public OrderStatus status() {
        return status;
    }
}