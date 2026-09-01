package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.idempotency.ProcessingResult;

public record RetryOutcome(ProcessingResult result, int attempts) {

    public RetryOutcome {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be positive");
        }
    }
}
