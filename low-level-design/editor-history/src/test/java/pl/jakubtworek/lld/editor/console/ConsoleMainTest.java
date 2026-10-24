package pl.jakubtworek.lld.editor.console;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Scenariusz adaptera potwierdza, że odmowa nie kończy dalszej nawigacji.
 */
class ConsoleMainTest {

    @Test
    void consoleEditsBranchesAndContinuesAfterRefusal() throws Exception {
        var text = new StringWriter();
        ConsoleMain.run(new BufferedReader(new StringReader("insert 0 AB\ninsert 2 C\nundo\ninsert 2 X\nundo\nbranches\nredo 2\ncheckout 0\nundo\nshow\nq\n")), new PrintWriter(text, true));
        assertTrue(text.toString().contains("v2: ABC"));
        assertTrue(text.toString().contains("v3: ABX"));
        assertTrue(text.toString().contains("Odmowa:"));
        assertTrue(text.toString().contains("v0: "));
    }
}
