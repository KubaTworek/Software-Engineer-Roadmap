package pl.jakubtworek.marketplace.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weryfikuje autentyczność i replay webhooka na surowych bajtach requestu.
 * Parsowanie JSON przed HMAC może zmienić whitespace/kolejność pól. Timestamp
 * ogranicza okno replay, deliveryId zapewnia idempotencję, a MessageDigest.isEqual
 * unika zwykłego porównania zależnego od pierwszego różnego znaku. Rotacja sekretu
 * jest wspierana przez zestaw aktywnych kluczy (stary + nowy w okresie przejściowym).
 */
public final class WebhookSignatureVerifier {
    private final Set<String> acceptedSecrets;
    private final Duration tolerance;
    private final Set<String> consumedDeliveries = ConcurrentHashMap.newKeySet();

    public WebhookSignatureVerifier(Set<String> acceptedSecrets, Duration tolerance) {
        this.acceptedSecrets = Set.copyOf(acceptedSecrets);
        this.tolerance = tolerance;
    }

    public void verify(String deliveryId, Instant signedAt, byte[] rawBody, String signature, Instant now) {
        if (signedAt.isBefore(now.minus(tolerance)) || signedAt.isAfter(now.plus(tolerance))) {
            throw new SecurityException("webhook timestamp outside accepted window");
        }
        byte[] supplied;
        try { supplied = HexFormat.of().parseHex(signature); }
        catch (IllegalArgumentException malformed) { throw new SecurityException("malformed signature"); }
        boolean valid = acceptedSecrets.stream().map(secret -> sign(secret, signedAt, rawBody))
                .anyMatch(expected -> MessageDigest.isEqual(expected, supplied));
        if (!valid) throw new SecurityException("invalid webhook signature");
        if (!consumedDeliveries.add(deliveryId)) throw new SecurityException("replayed webhook delivery");
    }

    public static String signature(String secret, Instant signedAt, byte[] rawBody) {
        return HexFormat.of().formatHex(sign(secret, signedAt, rawBody));
    }

    private static byte[] sign(String secret, Instant signedAt, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(signedAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return mac.doFinal(body);
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}
