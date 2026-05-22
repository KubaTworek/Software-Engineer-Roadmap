package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.resiliance;

// Generic retry policy.
// It can be used around network calls, broker publishing, or external APIs.
public final class RetryPolicy {

    private final int maxAttempts;

    public RetryPolicy(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Max attempts must be positive");
        }

        this.maxAttempts = maxAttempts;
    }

    public void execute(Runnable action) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException exception) {
                lastError = exception;
            }
        }

        throw lastError;
    }
}