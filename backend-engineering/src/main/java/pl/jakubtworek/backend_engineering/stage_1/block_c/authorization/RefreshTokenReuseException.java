package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

public class RefreshTokenReuseException extends RuntimeException {

    public RefreshTokenReuseException() {
        super("Refresh token reuse detected; session family was revoked");
    }
}
