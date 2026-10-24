package pl.jakubtworek.lld.filesystem.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser ma zakończyć ocenę składni przed mutacją i zachować payload dosłownie.
 */
class CommandParserTest {

    @Test
    void quotedArgumentsEscapeAndPipeBelongToPath() {
        var c = CommandParser.parse("move \"/a space/\\\"x\\\"\" \"/b | name\"");
        assertEquals(List.of("/a space/\"x\"", "/b | name"), c.arguments());
        var create = CommandParser.parse("create \"/a | b\" |   tekst | \"bez końca");
        assertEquals(List.of("/a | b", "  tekst | \"bez końca"), create.arguments());
    }

    @Test
    void emptyPayloadAndWhitespaceBetweenArgumentsAreSupported() {
        assertEquals(List.of("/f", ""), CommandParser.parse("write /f | ").arguments());
        assertEquals(List.of("/a", "/b"), CommandParser.parse("  copy\t/a  /b ").arguments());
        assertThrows(UnsupportedOperationException.class, () -> CommandParser.parse("ls /").arguments().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = { "move \"/a /b", "move /a", "move /a /b /c", "ls / extra", "q extra", "ls / | x",
            "create /a", "create /a /b | x", "read \"/bad\\q\"", "unknown", "\"", "" })
    void invalidSyntaxIsRejected(String line) {
        assertThrows(IllegalArgumentException.class, () -> CommandParser.parse(line));
    }
}
