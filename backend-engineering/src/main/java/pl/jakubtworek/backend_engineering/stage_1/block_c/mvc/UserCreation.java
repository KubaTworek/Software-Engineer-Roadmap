package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

/** Result distinguishes the first execution from an idempotent replay. */
public record UserCreation(UserResponse user, boolean replayed) {
}
