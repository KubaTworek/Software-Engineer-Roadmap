package pl.jakubtworek.marketplace.ordering.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Kanoniczny odcisk requestu oddziela semantykę idempotencji od przypadkowej
 * implementacji {@code toString()}. Correlation-id nie należy do treści komendy:
 * retry tej samej intencji może przyjść z nowym identyfikatorem śledzenia.
 */
public final class RequestFingerprint {
    private RequestFingerprint() {}

    public static String sha256(String canonicalRequest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossibleOnJava21) {
            throw new IllegalStateException("SHA-256 must be available", impossibleOnJava21);
        }
    }
}
