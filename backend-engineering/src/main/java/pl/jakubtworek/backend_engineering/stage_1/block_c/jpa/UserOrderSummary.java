package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

public record UserOrderSummary(Long userId, String fullName, int orderCount) {

    static UserOrderSummary from(User user) {
        return new UserOrderSummary(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getOrders().size()
        );
    }
}
