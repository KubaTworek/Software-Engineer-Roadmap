package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
