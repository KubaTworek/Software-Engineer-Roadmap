package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Podpis obejmuje timestamp i surowe bajty payloadu, nie obiekt po deserializacji. */
public final class HmacWebhookSigner {

    private final byte[] secret;

    public HmacWebhookSigner(byte[] secret) {
        if (secret.length < 32) {
            throw new IllegalArgumentException("webhook secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    public String sign(long timestampEpochSecond, String payload) {
        String signed = timestampEpochSecond + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "v1=" + HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is required by the Java platform", exception);
        }
    }
}
