package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

public record UserCursor(String lastName, Long id) {

    public UserCursor {
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName must not be blank");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
    }
}
