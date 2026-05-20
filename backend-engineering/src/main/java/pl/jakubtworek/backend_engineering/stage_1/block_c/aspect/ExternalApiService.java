package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates retry aspect behavior.
 */
@Service
public class ExternalApiService {

    private final AtomicInteger counter = new AtomicInteger();

    /**
     * This method fails twice,
     * then succeeds on third attempt.
     */
    @RetryableOperation
    public String callExternalApi() {

        int currentAttempt = counter.incrementAndGet();

        if (currentAttempt < 3) {

            System.out.println(
                    "External API failure on attempt: "
                            + currentAttempt
            );

            throw new RuntimeException("Temporary API failure");
        }

        return "EXTERNAL_API_SUCCESS";
    }
}