package pl.jakubtworek.backend.notification.chaos;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class NotificationChaosSettings {
    private final long defaultProcessingDelayMs;
    private final AtomicLong processingDelayMs;

    public NotificationChaosSettings(@Value("${notification.processing-delay-ms:0}") long defaultProcessingDelayMs) {
        this.defaultProcessingDelayMs = defaultProcessingDelayMs;
        this.processingDelayMs = new AtomicLong(defaultProcessingDelayMs);
    }

    public long processingDelayMs() {
        return processingDelayMs.get();
    }

    public void setProcessingDelayMs(long value) {
        processingDelayMs.set(Math.max(0, value));
    }

    public void reset() {
        processingDelayMs.set(defaultProcessingDelayMs);
    }
}
