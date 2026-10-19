package pl.jakubtworek.backend_engineering.stage_3.block_b.tracing;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Versioned cache payload codec used by the tracing example.
 *
 * The codec is deliberately framework-neutral. The important lesson is the
 * contract: cached data has an explicit version, is validated after decoding,
 * and never silently falls back to fabricated domain values. A production
 * service would commonly implement the same boundary with JSON/Protobuf plus
 * schema evolution and corruption metrics.
 */
public final class OrderCacheCodec {

    private static final String VERSION = "v1";
    private static final String SEPARATOR = ":";

    public String encode(TracedOrderRepository.OrderRecord order) {
        if (order == null) throw new IllegalArgumentException("order is required");
        return String.join(
                SEPARATOR,
                VERSION,
                encodeText(order.id()),
                Long.toString(order.totalCents()),
                encodeText(order.currency())
        );
    }

    public TracedOrderRepository.OrderRecord decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("cached order payload must not be blank");
        }

        String[] fields = payload.split(SEPARATOR, -1);
        if (fields.length != 4 || !VERSION.equals(fields[0])) {
            throw new IllegalArgumentException("unsupported cached order schema");
        }

        try {
            return new TracedOrderRepository.OrderRecord(
                    decodeText(fields[1]),
                    Long.parseLong(fields[2]),
                    decodeText(fields[3])
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid cached order payload", exception);
        }
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
