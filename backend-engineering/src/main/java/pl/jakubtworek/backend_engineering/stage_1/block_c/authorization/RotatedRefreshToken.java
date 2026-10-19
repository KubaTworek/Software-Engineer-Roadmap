package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

/**
 * Result of refresh-token rotation. Keeping the owner with the new raw token
 * prevents the caller from guessing or hard-coding the JWT subject.
 */
public record RotatedRefreshToken(String rawToken, String username) {
}
