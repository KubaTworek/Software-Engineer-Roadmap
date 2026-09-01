package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
