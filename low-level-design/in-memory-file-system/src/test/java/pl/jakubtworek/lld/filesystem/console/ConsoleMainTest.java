package pl.jakubtworek.lld.filesystem.console;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sesje przechodzą przez parser bez globalnego System.setIn i bez plików hosta.
 */
class ConsoleMainTest {

    @Test
    void scenarioMovesFindsAndReadsText() {
        String result = run("mkdir /docs\ncreate /docs/a.txt | wiedza\nmkdir /archive\nmove /docs /archive/course\nfind / | .txt\nread /archive/course/a.txt\nq\n");
        assertTrue(result.contains("/archive/course/a.txt"));
        assertTrue(result.contains("wiedza"));
        assertFalse(result.contains("Odmowa:"));
    }

    @Test
    void cycleRefusalDoesNotLoseSource() {
        String result = run("mkdir /a\nmkdir /a/b\nmove /a /a/b/new\nls /\nq\n");
        assertTrue(result.contains("Odmowa: INVALID_MOVE"));
        assertTrue(result.contains("\n/a"));
    }

    @Test
    void malformedCommandsAndNonRecursiveDeleteDoNotStopSession() {
        String result = run("help\nunknown\ncreate /f\nmkdir /a\ncreate /a/f | x\nrm /a\nrm-tree /a\nls /\n");
        assertTrue(result.contains("Odmowa: Nieznana komenda"));
        assertTrue(result.contains("Odmowa: Wymagany separator"));
        assertTrue(result.contains("Odmowa: DIRECTORY_NOT_EMPTY"));
    }

    @Test
    void writesPreserveSpacesAndAllowEmptyTextAndPathWithSpaces() {
        String result = run("create \"/my file\" |   tekst  \nread \"/my file\"\nwrite \"/my file\" | \nread \"/my file\"\nq\n");
        assertTrue(result.contains("  tekst  "));
        assertFalse(result.contains("Odmowa:"));
    }

    @Test
    void copyQuotedPathsHistoricalViewsAndSearchWorkTogether() {
        String result = run("mkdir \"/my docs\"\ncreate \"/my docs/a.txt\" | old\nsave initial\n" + "copy \"/my docs\" \"/my copy\"\nwrite \"/my copy/a.txt\" | new\n" + "search \"/my copy\" FILE txt 1 a\nuse initial\nread \"/my docs/a.txt\"\n" + "mkdir /forbidden\nls /\nlive\nread \"/my copy/a.txt\"\nq\n");
        assertTrue(result.contains("/my copy/a.txt"));
        assertTrue(result.contains("old"));
        assertTrue(result.contains("new"));
        assertTrue(result.contains("Odmowa: Widok historyczny jest tylko do odczytu"));
        assertFalse(result.contains("/forbidden\n"));
    }

    @Test
    void malformedQuoteCannotDetachSourceAndErrorsShowBlockingPath() {
        String result = run("mkdir /a\nmove \"/a /b\nls /\ncreate /file | x\nmkdir /file/child\nq\n");
        assertTrue(result.contains("Odmowa: Niezamknięty cudzysłów"));
        assertTrue(result.contains("\n/a"));
        assertTrue(result.contains("NOT_DIRECTORY operation=MKDIR path=/file/child problem=/file"));
    }

    private static String run(String commands) {
        var result = new StringWriter();
        ConsoleMain.run(new StringReader(commands), new PrintWriter(result));
        return result.toString();
    }
}
