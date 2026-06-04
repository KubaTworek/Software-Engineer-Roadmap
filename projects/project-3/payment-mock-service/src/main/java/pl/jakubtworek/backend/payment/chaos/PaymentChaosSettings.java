package pl.jakubtworek.backend.payment.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class PaymentChaosSettings {
    private final double defaultFailureRate;
    private final int defaultMaxDelayMs;
    private final AtomicReference<Double> failureRate;
    private final AtomicReference<Integer> maxDelayMs;

    public PaymentChaosSettings(@Value("${payment.failure-rate:0.2}") double defaultFailureRate,
                                @Value("${payment.max-delay-ms:1500}") int defaultMaxDelayMs) {
        this.defaultFailureRate = defaultFailureRate;
        this.defaultMaxDelayMs = defaultMaxDelayMs;
        this.failureRate = new AtomicReference<>(defaultFailureRate);
        this.maxDelayMs = new AtomicReference<>(defaultMaxDelayMs);
    }

    public double failureRate() {
        return failureRate.get();
    }

    public int maxDelayMs() {
        return maxDelayMs.get();
    }

    public void setFailureRate(double value) {
        failureRate.set(Math.max(0.0, Math.min(1.0, value)));
    }

    public void setMaxDelayMs(int value) {
        maxDelayMs.set(Math.max(0, value));
    }

    public void reset() {
        failureRate.set(defaultFailureRate);
        maxDelayMs.set(defaultMaxDelayMs);
    }
}
