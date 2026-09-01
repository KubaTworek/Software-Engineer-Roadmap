package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.retry;

import java.time.Duration;

@FunctionalInterface
public interface RetrySleeper {

    void sleep(Duration delay) throws InterruptedException;
}
