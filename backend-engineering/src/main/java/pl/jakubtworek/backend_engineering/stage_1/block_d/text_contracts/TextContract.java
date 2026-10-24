package pl.jakubtworek.backend_engineering.stage_1.block_d.text_contracts;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;

/** Kontrakt tekstu musi nazwać jednostkę limitu i regułę równoważności. */
public final class TextContract {
    private TextContract() { }
    public static String displayName(String raw, int maxCodePoints, int maxUtf8Bytes) {
        Objects.requireNonNull(raw);
        if (maxCodePoints < 1 || maxUtf8Bytes < 1) throw new IllegalArgumentException("Dodatnie limity");
        String normalized = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
        if (normalized.isBlank() || normalized.codePoints().anyMatch(Character::isISOControl)
                || normalized.codePointCount(0, normalized.length()) > maxCodePoints
                || utf8(normalized).length > maxUtf8Bytes)
            throw new IllegalArgumentException("Nazwa przekracza kontrakt tekstowy");
        return normalized;
    }
    public static byte[] utf8(String text) {
        try {
            // String.getBytes zastępuje niepoprawny surrogate. Na granicy danych odrzucamy go jawnie.
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            byte[] result = new byte[bytes.remaining()]; bytes.get(result); return result;
        } catch (CharacterCodingException malformed) { throw new IllegalArgumentException("Niepoprawny Unicode", malformed); }
    }
    public static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException malformed) { throw new IllegalArgumentException("Niepoprawne UTF-8", malformed); }
    }
    /** Odpowiada porządkowi bajtowemu PostgreSQL COLLATE "C" w bazie UTF-8. */
    public static Comparator<String> utf8ByteOrder() { return (a, b) -> Arrays.compareUnsigned(utf8(a), utf8(b)); }
}
