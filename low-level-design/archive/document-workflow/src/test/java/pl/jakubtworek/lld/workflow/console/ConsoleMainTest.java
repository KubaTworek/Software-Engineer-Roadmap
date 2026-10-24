package pl.jakubtworek.lld.workflow.console;

import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Przechodzimy przez parser i tekstowe wyjście bez podmiany globalnych strumieni.
 */
class ConsoleMainTest {

    @Test
    void rejectionCorrectionAndStaleTokenCanBeReproducedInDemo() {
        String output = run("submit\nas bob\napprove\nas carol\nreject Brak kosztu\nas alice\n" + "edit Projekt | Uzupełniona treść\nsubmit\nas bob\napprove 1 1 technical\napprove\nas carol\napprove\nhistory\nq\n");
        assertTrue(output.contains("stan: REJECTED; rewizja: 1; runda: 1"));
        assertTrue(output.contains("stan: APPROVED; rewizja: 2; runda: 2"));
        assertTrue(output.contains("Odmowa: STALE_REVIEW"));
        assertTrue(output.contains("REJECTED rev=1 round=1 stage=manager note=Brak kosztu"));
        assertTrue(output.contains("STAGE_APPROVED rev=2 round=2 stage=manager"));
    }

    @Test
    void invalidNewOrEditDoesNotReplaceExistingDocument() {
        String output = run("as bob\nnew OTHER Inny | Treść\nas alice\nedit Nowy tytuł | Nowa treść\n" + "edit   |   \nnew BAD brak separatora\nshow\nq\n");
        assertTrue(output.contains("Odmowa: FORBIDDEN"));
        assertTrue(output.contains("Dokument DOC1; stan: DRAFT; rewizja: 2"));
        assertTrue(output.contains("Nowy tytuł | Nowa treść"));
        assertFalse(output.contains("Dokument OTHER"));
    }

    @Test
    void authorWithReviewerRoleStillCannotReviewAndReasonIsRequired() {
        String output = run("submit\napprove\nas bob\nreject\napprove\nshow\n");
        assertTrue(output.contains("Odmowa: SELF_REVIEW"));
        assertTrue(output.contains("Odmowa: REASON_REQUIRED"));
        assertTrue(output.contains("Token: 1 1 manager"));
        assertTrue(output.contains("stan: IN_REVIEW; rewizja: 1; runda: 1"));
    }

    @Test
    void helpPlanUnknownActorAndEofDoNotBreakSession() {
        String output = run("help\nplan\nas unknown\nas dave\nas alice\nsubmit\nas dave\napprove\napprove\nshow\n");
        assertTrue(output.contains("technical: REVIEWER"));
        assertTrue(output.contains("manager: MANAGER"));
        assertTrue(output.contains("Odmowa: Nieznany aktor"));
        assertTrue(output.contains("stan: APPROVED"));
    }

    @Test
    void versionsWithdrawalArchiveAndPolicyCanBeExploredFromConsole() {
        String output = run("expect 1\nedit Projekt | Druga treść\nsubmit\nlatest\nsubmit\n" + "withdraw Nowe wymagania\nsubmit\nas bob\napprove 1 2 technical\narchive\n" + "as alice\npolicy DISTINCT_PER_ROUND\nnew DOC2 Plan | Treść\nsubmit\nas dave\napprove\napprove\nactions\nq\n");
        assertTrue(output.contains("Odmowa: STALE_VERSION"));
        assertTrue(output.contains("Odmowa: STALE_REVIEW"));
        assertTrue(output.contains("ostatnia runda: 2"));
        assertTrue(output.contains("Druga treść"));
        assertTrue(output.contains("Odmowa: REVIEWER_ALREADY_USED"));
        assertTrue(output.contains("APPROVE: CommandAssessment"));
    }

    @Test
    void invalidExpectedVersionDoesNotReplacePreviouslySelectedVersion() {
        String output = run("expect 1\nexpect -1\nsubmit\nwithdraw Powrót\nlatest\nwithdraw Powrót\nhistory\nq\n");
        assertTrue(output.contains("Odmowa: Wersja musi być dodatnia"));
        assertTrue(output.contains("Odmowa: STALE_VERSION"));
        assertTrue(output.contains("WITHDRAWN rev=1 round=1 stage=technical note=Powrót"));
    }

    private static String run(String commands) {
        var output = new StringWriter();
        ConsoleMain.run(new StringReader(commands), new PrintWriter(output));
        return output.toString();
    }
}
