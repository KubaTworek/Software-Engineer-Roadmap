package pl.jakubtworek.lld.workflow.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowException.Reason.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowCommand.Operation.*;

/**
 * Dowody reguł przyrostu: wersja chroni intencję, runda chroni decyzję,
 * a archiwum pozwala zobaczyć tekst, którego dotyczyła konkretna decyzja.
 */
class WorkflowEnhancementsTest {

    static final Actor AUTHOR = actor("alice", Role.AUTHOR, Role.REVIEWER, Role.MANAGER);
    static final Actor BOTH = actor("dave", Role.REVIEWER, Role.MANAGER);
    static final Actor OTHER = actor("carol", Role.MANAGER);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

    static Actor actor(String id, Role... roles) {
        return new Actor(new UserId(id), Set.of(roles));
    }

    static Document document(ReviewerPolicies policy) {
        return new Document(new DocumentId("DOC"), AUTHOR, new DocumentContent("T", "v1"),
                new WorkflowDefinition(List.of(new ReviewStage("technical", Role.REVIEWER), new ReviewStage("manager",
                Role.MANAGER))), CLOCK, policy);
    }

    @Test
    void everyMutationRequiresVersionAndStaleIntentCannotOverwriteNewText() {
        var d = document(ReviewerPolicies.ALLOW_REUSE);
        var old = d.snapshot();
        d.edit(AUTHOR, old.version(), new DocumentContent("T", "v2"));
        var now = d.snapshot();
        var token = new ReviewToken(now.id(), 1, 1, "technical");
        for (WorkflowCommand c : List.of(new WorkflowCommand.Edit(new DocumentContent("T", "lost")),
                new WorkflowCommand.Submit(), new WorkflowCommand.Approve(token), new WorkflowCommand.Reject(token,
                "x"), new WorkflowCommand.Withdraw("x"))) {
            assertEquals(Optional.of(STALE_VERSION), d.assess(AUTHOR, old.version(), c).refusal());
            assertEquals(STALE_VERSION, assertThrows(WorkflowException.class, () -> d.execute(AUTHOR, old.version(), c))
                    .reason());
            assertSame(now, d.snapshot());
        }
        assertEquals("v1", old.content().body());
        assertEquals("v2", now.content().body());
    }

    @Test
    void availabilityIsPreparationAndAssessmentChecksActualPayload() {
        var d = document(ReviewerPolicies.ALLOW_REUSE);
        assertTrue(d.availableOperations(AUTHOR).operations().get(EDIT).allowed());
        assertEquals(Optional.of(UNCHANGED_CONTENT), d.assess(AUTHOR, 1, new WorkflowCommand.Edit(d.snapshot()
                .content())).refusal());
        var intent = new WorkflowCommand.Submit();
        assertTrue(d.assess(AUTHOR, 1, intent).allowed());
        d.edit(AUTHOR, 1, new DocumentContent("T", "v2"));
        assertEquals(STALE_VERSION, assertThrows(WorkflowException.class, () -> d.execute(AUTHOR, 1, intent)).reason());
        d.submit(AUTHOR, 2);
        assertTrue(d.availableOperations(BOTH).operations().get(REJECT).allowed());
        assertEquals(Optional.of(REASON_REQUIRED), d.assess(BOTH, 3, new WorkflowCommand.Reject(d.reviewToken()
                .orElseThrow(), " ")).refusal());
        assertThrows(UnsupportedOperationException.class, () -> d.availableOperations(AUTHOR).operations().clear());
    }

    @ParameterizedTest
    @EnumSource(ReviewerPolicies.class)
    void reviewerPolicyChangesReuseButNeverAuthorProhibition(ReviewerPolicies policy) {
        var d = document(policy);
        d.submit(AUTHOR, 1);
        assertEquals(SELF_REVIEW, assertThrows(WorkflowException.class, () -> d.approve(AUTHOR, 2, d.reviewToken()
                .orElseThrow())).reason());
        d.approve(BOTH, 2, d.reviewToken().orElseThrow());
        var token = d.reviewToken().orElseThrow();
        for (WorkflowCommand c : List.of(new WorkflowCommand.Approve(token), new WorkflowCommand.Reject(token, "x"))) {
            assertEquals(policy == ReviewerPolicies.ALLOW_REUSE, d.assess(BOTH, 3, c).allowed());
        }
        if (policy == ReviewerPolicies.DISTINCT_PER_ROUND) {
            assertEquals(Optional.of(REVIEWER_ALREADY_USED), d.availableOperations(BOTH).operations().get(APPROVE)
                    .refusal());
            assertEquals(REVIEWER_ALREADY_USED, assertThrows(WorkflowException.class, () -> d.reject(BOTH, 3, token,
                    "x")).reason());
        }
        d.reject(OTHER, 3, token, "popraw");
        d.edit(AUTHOR, 4, new DocumentContent("T", "v2"));
        d.submit(AUTHOR, 5);
        assertTrue(d.assess(BOTH, 6, new WorkflowCommand.Approve(d.reviewToken().orElseThrow())).allowed());
    }

    @Test
    void withdrawalPreservesRoundCounterInvalidatesTokensAndNeedsNoContentEdit() {
        var d = document(ReviewerPolicies.DISTINCT_PER_ROUND);
        for (int cycle = 1; cycle <= 3; cycle++) {
            int round = 2 * cycle - 1;
            d.submit(AUTHOR, d.snapshot().version());
            var token = d.reviewToken().orElseThrow();
            assertEquals(round, token.round());
            d.approve(BOTH, d.snapshot().version(), token);
            var before = d.snapshot();
            assertEquals(REASON_REQUIRED, assertThrows(WorkflowException.class, () -> d.withdraw(AUTHOR,
                    before.version(), " ")).reason());
            assertSame(before, d.snapshot());
            d.withdraw(AUTHOR, before.version(), "  zmiana planu  ");
            assertEquals(DocumentStatus.DRAFT, d.snapshot().status());
            assertTrue(d.reviewToken().isEmpty());
            assertTrue(d.snapshot().review().isEmpty());
            assertEquals(round, d.snapshot().lastRound());
            assertEquals("zmiana planu", d.snapshot().history().getLast().note().orElseThrow());
            d.submit(AUTHOR, d.snapshot().version());
            assertEquals(STALE_REVIEW, assertThrows(WorkflowException.class, () -> d.approve(BOTH, d.snapshot()
                    .version(), token)).reason());
            assertTrue(d.snapshot().review().orElseThrow().approvals().isEmpty());
            // Jeden cykl obejmuje dwa zgłoszenia tej samej treści. Wycofujemy
            // także drugą rundę, żeby kolejny cykl zastał znów DRAFT.
            d.withdraw(AUTHOR, d.snapshot().version(), "ponownie");
        }
        assertEquals(1, d.snapshot().revision());
        assertEquals(1, d.snapshot().revisions().size());
        assertEquals(d.snapshot().version(), d.snapshot().history().size());
    }

    @Test
    void archiveKeepsFullTextAndMetadataAndOldViewsCannotChange() {
        var d = document(ReviewerPolicies.ALLOW_REUSE);
        var first = d.snapshot();
        d.edit(AUTHOR, 1, new DocumentContent("T2", "pełny drugi tekst"));
        d.submit(AUTHOR, 2);
        d.reject(BOTH, 3, d.reviewToken().orElseThrow(), "popraw");
        d.edit(AUTHOR, 4, new DocumentContent("T3", "trzeci tekst"));
        assertEquals(3, d.snapshot().revision());
        assertEquals(5, d.snapshot().version());
        assertEquals("pełny drugi tekst", d.snapshot().review().orElseThrow().submittedContent().body());
        var archived = d.archivedRevision(2).orElseThrow();
        assertEquals(AUTHOR.id(), archived.editor());
        assertEquals(CLOCK.instant(), archived.at());
        assertEquals(d.snapshot().history().get(1).at(), archived.at());
        assertEquals(1, first.revisions().size());
        assertThrows(UnsupportedOperationException.class, () -> first.revisions().clear());
        assertTrue(d.archivedRevision(0).isEmpty());
        assertTrue(d.archivedRevision(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void assessmentsDoNotReadClockAndClockFailureCannotPublishPartialArchive() {
        class FailingClock extends Clock {

            int calls;

            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                if (++calls > 1)
                    throw new IllegalStateException("clock");
                return CLOCK.instant();
            }
        }
        var clock = new FailingClock();
        var d = new Document(new DocumentId("D"), AUTHOR, new DocumentContent("T", "v1"),
                new WorkflowDefinition(List.of(new ReviewStage("one", Role.REVIEWER))), clock);
        var before = d.snapshot();
        for (int i = 0; i < 5; i++) {
            d.availableOperations(AUTHOR);
            assertTrue(d.assess(AUTHOR, 1, new WorkflowCommand.Submit()).allowed());
        }
        assertEquals(1, clock.calls);
        assertThrows(IllegalStateException.class, () -> d.edit(AUTHOR, 1, new DocumentContent("T", "v2")));
        assertSame(before, d.snapshot());
        assertEquals(STALE_VERSION, assertThrows(WorkflowException.class, () -> d.submit(AUTHOR, 0)).reason());
        assertEquals(2, clock.calls);
    }
}
