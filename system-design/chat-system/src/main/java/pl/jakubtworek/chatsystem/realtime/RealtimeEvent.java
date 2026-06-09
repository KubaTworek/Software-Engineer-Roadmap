package pl.jakubtworek.chatsystem.realtime;

import java.time.Instant;

public record RealtimeEvent<T>(
        String type,
        T payload,
        Instant occurredAt
) {
    public static <T> RealtimeEvent<T> of(String type, T payload) {
        return new RealtimeEvent<>(type, payload, Instant.now());
    }
}
