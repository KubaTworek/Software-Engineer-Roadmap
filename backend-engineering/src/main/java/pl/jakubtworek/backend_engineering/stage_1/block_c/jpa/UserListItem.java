package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

public record UserListItem(Long id, String firstName, String lastName, int age) {

    static UserListItem from(User user) {
        return new UserListItem(user.getId(), user.getFirstName(), user.getLastName(), user.getAge());
    }
}
