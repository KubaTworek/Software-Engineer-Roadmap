package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeCommandDecoderTest {

    private final SafeCommandDecoder decoder = new SafeCommandDecoder();

    @Test
    void allowlistedTypeAndExactSchemaAreDecoded() {
        assertThat(decoder.decode(Map.of("type", "email-change", "email", "alice@example.com")))
                .isEqualTo(new SafeCommandDecoder.EmailChange("alice@example.com"));
    }

    @Test
    void classNameUnknownTypeAndUnexpectedFieldAreRejected() {
        assertRejected(Map.of("type", "java.lang.Runtime", "command", "calc"));
        assertRejected(Map.of("type", "admin-command", "role", "root"));
        assertRejected(Map.of("type", "profile-update", "displayName", "Alice", "isAdmin", "true"));
    }

    private void assertRejected(Map<String, String> payload) {
        assertThatThrownBy(() -> decoder.decode(payload))
                .isInstanceOf(SafeCommandDecoder.UnsafeCommandException.class);
    }
}
