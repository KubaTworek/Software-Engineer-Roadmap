package pl.jakubtworek.booking.entity;

import java.nio.*;
import java.nio.charset.*;
import java.text.Normalizer;

/** Nazwa osoby jest tekstem prezentacyjnym. Nie tworzymy z niej klucza tożsamości. */
public final class CustomerTextPolicy {
    private CustomerTextPolicy() { }
    public static String name(String raw) {
        if (raw == null) throw new IllegalArgumentException("Customer name required");
        String value = Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
        if (value.isBlank() || value.codePointCount(0, value.length()) > 255
                || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Customer name must contain 1..255 code points without controls");
        try {
            StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        } catch (CharacterCodingException invalid) { throw new IllegalArgumentException("Invalid Unicode name", invalid); }
        return value;
    }
}
