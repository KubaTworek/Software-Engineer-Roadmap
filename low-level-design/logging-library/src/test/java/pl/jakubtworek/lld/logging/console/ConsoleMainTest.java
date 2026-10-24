package pl.jakubtworek.lld.logging.console;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tekstowe sesje przechodzą przez parser i konfigurację bez globalnego System.setIn.
 */
class ConsoleMainTest {

    @Test
    void filteringAndJsonCanBeExplored() {
        String result = run("log DEBUG hidden\nlevel DEBUG\nlog DEBUG visible\nformat JSON\nlog ERROR błąd\nhistory\nq\n");
        assertTrue(result.contains("memory: FILTERED"));
        assertTrue(result.contains("memory: WRITTEN"));
        assertTrue(result.contains("\"level\":\"ERROR\""));
        assertTrue(result.contains("memory> {\"timestamp\""));
        assertFalse(result.contains("hidden\n"));
    }

    @Test
    void continueAndStopShowDifferentLaterOutcomeButKeepEarlierWrite() {
        String result = run("fail on\nlog ERROR first\npolicy STOP_ON_FAILURE\nlog ERROR second\nhistory\nq\n");
        assertTrue(result.contains("unstable: FAILED"));
        assertTrue(result.contains("console: WRITTEN"));
        assertTrue(result.contains("console: NOT_ATTEMPTED"));
        assertTrue(result.contains("memory> 2026-10-23T12:00:00Z ERROR [demo] second"));
    }

    @Test
    void invalidConfigurationDoesNotBreakSessionAndTargetThresholdWorks() {
        String result = run("help\npolicy BAD\nformat BAD\ntarget missing INFO\nlevel NOPE\n" + "target console ERROR\nlog INFO only-memory\nclear\nhistory\nunknown\n");
        assertTrue(result.contains("Odmowa:"));
        assertTrue(result.contains("console: FILTERED"));
        assertTrue(result.contains("memory: WRITTEN"));
    }

    @Test
    void eofAndEmptyMessageAreSupported() {
        String result = run("log INFO \nlog\nlog UNKNOWN text\nfail wrong\nhistory extra\n");
        assertTrue(result.contains("memory: WRITTEN"));
        assertTrue(result.contains("Odmowa: log LEVEL MESSAGE"));
        assertTrue(result.contains("Odmowa: fail on|off"));
    }

    @Test
    void contextMaskingAndBoundedRejectionAreVisible() {
        String result = run("context requestId r1\ncontext password raw-secret\nformat JSON\n" + "buffer 1 REJECT_NEW\nlog INFO first\npolicy STOP_ON_FAILURE\nlog INFO second\n" + "history\nbuffer-info\nclear\ncontext-clear\nlog INFO after\nq\n");
        assertTrue(result.contains("\"password\":\"***\""));
        assertTrue(result.contains("\"requestId\":\"r1\""));
        assertFalse(result.contains("raw-secret"));
        assertTrue(result.contains("memory: FAILED"));
        assertTrue(result.contains("console: NOT_ATTEMPTED"));
        assertTrue(result.contains("rejected=1"));
    }

    @Test
    void dropOldestAndInvalidConfigurationPreserveExpectedHistory() {
        String result = run("buffer 1 DROP_OLDEST\ncontext requestId r1\nmask bad,key,\n" + "buffer 0 REJECT_NEW\nlog INFO old\nlog INFO new\nbuffer-info\nhistory\nunset requestId\nq\n");
        assertTrue(result.contains("dropped=1"));
        assertTrue(result.contains("Odmowa:"));
        assertTrue(result.contains("memory> 2026-10-23T12:00:00Z INFO [demo] new"));
        assertFalse(result.contains("memory> 2026-10-23T12:00:00Z INFO [demo] old"));
    }

    private static String run(String commands) {
        var output = new StringWriter();
        ConsoleMain.run(new StringReader(commands), new PrintWriter(output));
        return output.toString();
    }
}
