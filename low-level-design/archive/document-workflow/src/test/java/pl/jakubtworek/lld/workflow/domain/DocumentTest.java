package pl.jakubtworek.lld.workflow.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.workflow.domain.DocumentStatus.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowException.Reason.*;

/**
 * Testy opisują reguły przez obserwowalne snapshoty i odmowy. Macierz obejmuje
 * wszystkie cztery stany i cztery pierwotne operacje; nowe wycofanie badają
 * WorkflowEnhancementsTest i WorkflowModelTest. Sprawdzamy stan i odmowy,
 * zamiast wiązać test z nazwą prywatnej metody publikującej wynik.
 */
class DocumentTest {

    private static final Actor AUTHOR = new Actor(new UserId("alice"), Set.of(Role.AUTHOR));
    private static final Actor REVIEWER = new Actor(new UserId("bob"), Set.of(Role.REVIEWER));
    private static final Actor MANAGER = new Actor(new UserId("carol"), Set.of(Role.MANAGER));
    private static final DocumentContent CONTENT = new DocumentContent("Projekt", "Wersja pierwsza");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void singleStageSubmissionAndApprovalFinishDocument() {
        var document = document(1, CLOCK);
        assertEquals(DRAFT, document.snapshot().status());
        assertTrue(document.reviewToken().isEmpty());
        var submitted = document.submit(AUTHOR, document.snapshot().version());
        assertEquals(1, submitted.revision());
        assertEquals(1, submitted.review().orElseThrow().number());
        document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
        assertEquals(APPROVED, document.snapshot().status());
        assertTrue(document.currentStage().isEmpty());
        assertEquals(List.of(WorkflowEvent.Type.CREATED, WorkflowEvent.Type.SUBMITTED,
                WorkflowEvent.Type.STAGE_APPROVED), document.snapshot().history().stream().map(WorkflowEvent::type)
                .toList());
    }

    @Test
    void twoStagesRequireCorrectRoleAndDoNotFinishAfterFirstApproval() {
        var document = document(2, CLOCK);
        document.submit(AUTHOR, document.snapshot().version());
        var first = document.reviewToken().orElseThrow();
        reject(document, () -> document.approve(MANAGER, document.snapshot().version(), first), FORBIDDEN);
        document.approve(REVIEWER, document.snapshot().version(), first);
        assertEquals(IN_REVIEW, document.snapshot().status());
        assertEquals("manager", document.currentStage().orElseThrow().id());
        reject(document, () -> document.approve(REVIEWER, document.snapshot().version(), document.reviewToken()
                .orElseThrow()), FORBIDDEN);
        reject(document, () -> document.approve(MANAGER, document.snapshot().version(), first), STALE_REVIEW);
        document.approve(MANAGER, document.snapshot().version(), document.reviewToken().orElseThrow());
        assertEquals(APPROVED, document.snapshot().status());
        assertEquals(List.of("technical", "manager"), document.snapshot().review().orElseThrow().approvals().stream()
                .map(StageApproval::stageId).toList());
    }

    @Test
    void rejectionRequiresCorrectionAndRestartsEveryStageWithoutLosingHistory() {
        var document = document(2, CLOCK);
        document.submit(AUTHOR, document.snapshot().version());
        document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
        var stale = document.reviewToken().orElseThrow();
        document.reject(MANAGER, document.snapshot().version(), stale, "  Brak uzasadnienia kosztu  ");
        var rejected = document.snapshot();
        assertEquals(REJECTED, rejected.status());
        assertEquals("Brak uzasadnienia kosztu", rejected.history().getLast().note().orElseThrow());
        reject(document, () -> document.submit(AUTHOR, document.snapshot().version()), NEW_REVISION_REQUIRED);
        document.edit(AUTHOR, document.snapshot().version(), new DocumentContent("Projekt", "Wersja poprawiona"));
        assertEquals(REJECTED, document.snapshot().status());
        assertEquals(CONTENT, document.snapshot().review().orElseThrow().submittedContent());
        document.submit(AUTHOR, document.snapshot().version());
        assertEquals(2, document.snapshot().revision());
        assertEquals(2, document.snapshot().review().orElseThrow().number());
        assertTrue(document.snapshot().review().orElseThrow().approvals().isEmpty());
        assertEquals("technical", document.currentStage().orElseThrow().id());
        reject(document, () -> document.approve(MANAGER, document.snapshot().version(), stale), STALE_REVIEW);
        document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
        document.approve(MANAGER, document.snapshot().version(), document.reviewToken().orElseThrow());
        assertEquals(APPROVED, document.snapshot().status());
        assertEquals(8, document.snapshot().history().size());
        assertEquals(1, rejected.revision());
        assertEquals(1, rejected.review().orElseThrow().approvals().size());
        assertTrue(document.snapshot().history().stream().anyMatch(e -> e.type() == WorkflowEvent.Type.REJECTED
                && e.round() == 1));
    }

    @ParameterizedTest
    @CsvSource({ "DRAFT,edit,OK", "DRAFT,submit,OK", "DRAFT,approve,WRONG_STATE", "DRAFT,reject,WRONG_STATE",
            "IN_REVIEW,edit,WRONG_STATE", "IN_REVIEW,submit,WRONG_STATE", "IN_REVIEW,approve,OK", "IN_REVIEW,reject,OK",
            "REJECTED,edit,OK", "REJECTED,submit,NEW_REVISION_REQUIRED", "REJECTED,approve,WRONG_STATE",
            "REJECTED,reject,WRONG_STATE", "APPROVED,edit,WRONG_STATE", "APPROVED,submit,WRONG_STATE",
            "APPROVED,approve,WRONG_STATE", "APPROVED,reject,WRONG_STATE" })
    void transitionMatrix(DocumentStatus status, String operation, String expected) {
        var document = document(1, CLOCK);
        var fallback = new ReviewToken(document.snapshot().id(), 1, 1, "technical");
        if (status != DRAFT)
            document.submit(AUTHOR, document.snapshot().version());
        if (status == REJECTED)
            document.reject(REVIEWER, document.snapshot().version(), fallback, "Popraw");
        if (status == APPROVED)
            document.approve(REVIEWER, document.snapshot().version(), fallback);
        Runnable command = switch(operation) {
            case "edit" ->
                () -> document.edit(AUTHOR, document.snapshot().version(), new DocumentContent("Projekt",
                        "Zmieniona treść"));
            case "submit" ->
                () -> document.submit(AUTHOR, document.snapshot().version());
            case "approve" ->
                () -> document.approve(REVIEWER, document.snapshot().version(), fallback);
            case "reject" ->
                () -> document.reject(REVIEWER, document.snapshot().version(), fallback, "Popraw");
            default ->
                throw new AssertionError(operation);
        };
        if (expected.equals("OK"))
            assertDoesNotThrow(command::run);
        else
            reject(document, command, WorkflowException.Reason.valueOf(expected));
    }

    @Test
    void ownershipAndRoleAreBothRequiredForAuthorCommands() {
        var document = document(1, CLOCK);
        var otherAuthor = new Actor(new UserId("eve"), Set.of(Role.AUTHOR));
        var authorWithoutRole = new Actor(AUTHOR.id(), Set.of());
        reject(document, () -> document.edit(otherAuthor, document.snapshot().version(), CONTENT), FORBIDDEN);
        reject(document, () -> document.submit(otherAuthor, document.snapshot().version()), FORBIDDEN);
        reject(document, () -> document.submit(authorWithoutRole, document.snapshot().version()), FORBIDDEN);
        assertThrows(WorkflowException.class, () -> new Document(new DocumentId("X"), REVIEWER, CONTENT, plan(1),
                CLOCK));
    }

    @Test
    void authorCannotReviewEvenWithAllRolesAndNewActorInstance() {
        var document = document(1, CLOCK);
        document.submit(AUTHOR, document.snapshot().version());
        var powerfulAuthor = new Actor(new UserId("alice"), Set.of(Role.AUTHOR, Role.REVIEWER, Role.MANAGER));
        reject(document, () -> document.approve(powerfulAuthor, document.snapshot().version(), document.reviewToken()
                .orElseThrow()), SELF_REVIEW);
        reject(document, () -> document.reject(powerfulAuthor, document.snapshot().version(), document.reviewToken()
                .orElseThrow(), "Nie"), SELF_REVIEW);
    }

    @Test
    void oneOtherPersonWithBothRolesMayApproveBothStagesByChosenPolicy() {
        var document = document(2, CLOCK);
        document.submit(AUTHOR, document.snapshot().version());
        var reviewerManager = new Actor(new UserId("dave"), Set.of(Role.REVIEWER, Role.MANAGER));
        document.approve(reviewerManager, document.snapshot().version(), document.reviewToken().orElseThrow());
        document.approve(reviewerManager, document.snapshot().version(), document.reviewToken().orElseThrow());
        assertEquals(APPROVED, document.snapshot().status());
    }

    @ParameterizedTest
    @ValueSource(strings = { "document", "round", "revision", "stage" })
    void tokenMustMatchEveryComponent(String changed) {
        var document = document(1, CLOCK);
        document.submit(AUTHOR, document.snapshot().version());
        ReviewToken correct = document.reviewToken().orElseThrow();
        var stale = new ReviewToken(changed.equals("document") ? new DocumentId("OTHER") : correct.documentId(),
                changed.equals("round") ? 2 : 1, changed.equals("revision") ? 2 : 1,
                changed.equals("stage") ? "other" : "technical");
        reject(document, () -> document.approve(REVIEWER, document.snapshot().version(), stale), STALE_REVIEW);
        reject(document, () -> document.reject(REVIEWER, document.snapshot().version(), stale, "Nie"), STALE_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t", "\n" })
    void blankRejectionReasonDoesNotConsumeDecision(String reason) {
        var document = document(1, CLOCK);
        document.submit(AUTHOR, document.snapshot().version());
        var token = document.reviewToken().orElseThrow();
        reject(document, () -> document.reject(REVIEWER, document.snapshot().version(), token, reason),
                REASON_REQUIRED);
        document.approve(REVIEWER, document.snapshot().version(), token);
        assertEquals(APPROVED, document.snapshot().status());
    }

    @Test
    void identicalNormalizedContentDoesNotCreateARevision() {
        var document = document(1, CLOCK);
        reject(document, () -> document.edit(AUTHOR, document.snapshot().version(), new DocumentContent(" Projekt ",
                " Wersja pierwsza ")), UNCHANGED_CONTENT);
        document.edit(AUTHOR, document.snapshot().version(), new DocumentContent("Projekt nowy", "Treść nowa"));
        assertEquals(2, document.snapshot().revision());
        assertEquals(0, document.snapshot().history().getLast().round());
    }

    @Test
    void permissionAndStateChecksPrecedeReasonValidation() {
        var document = document(1, CLOCK);
        var token = new ReviewToken(document.snapshot().id(), 1, 1, "technical");
        reject(document, () -> document.reject(REVIEWER, document.snapshot().version(), token, null), WRONG_STATE);
        document.submit(AUTHOR, document.snapshot().version());
        reject(document, () -> document.reject(MANAGER, document.snapshot().version(), token, null), FORBIDDEN);
        reject(document, () -> document.reject(REVIEWER, document.snapshot().version(), token, null), REASON_REQUIRED);
    }

    @Test
    void snapshotsRolesAndDefinitionCannotBeMutatedThroughCollections() {
        var stages = new ArrayList<>(plan(2).stages());
        var definition = new WorkflowDefinition(stages);
        var roles = new java.util.HashSet<>(Set.of(Role.AUTHOR));
        var author = new Actor(AUTHOR.id(), roles);
        var document = new Document(new DocumentId("DOC"), author, CONTENT, definition, CLOCK);
        stages.clear();
        roles.clear();
        var draft = document.snapshot();
        document.submit(author, document.snapshot().version());
        var inReview = document.snapshot();
        document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
        assertEquals(1, draft.history().size());
        assertTrue(inReview.review().orElseThrow().approvals().isEmpty());
        assertEquals(2, document.definition().stages().size());
        assertThrows(UnsupportedOperationException.class, () -> draft.history().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.definition().stages().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.snapshot().review().orElseThrow().approvals()
                .clear());
        assertThrows(UnsupportedOperationException.class, () -> author.roles().clear());
    }

    @Test
    void oneClockReadPerAcceptedCommandAndNoneForRejection() {
        var reads = new AtomicInteger();
        Clock clock = clock(() -> CLOCK.instant().plusSeconds(reads.getAndIncrement()));
        var document = document(1, clock);
        assertEquals(1, reads.get());
        reject(document, () -> document.submit(MANAGER, document.snapshot().version()), FORBIDDEN);
        assertEquals(1, reads.get());
        document.submit(AUTHOR, document.snapshot().version());
        document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
        assertEquals(3, reads.get());
        var approval = document.snapshot().review().orElseThrow().approvals().getFirst();
        assertEquals(document.snapshot().history().getLast().at(), approval.at());
    }

    @Test
    void clockFailureDoesNotPublishPartialTransition() {
        var fail = new java.util.concurrent.atomic.AtomicBoolean(false);
        Clock clock = clock(() -> {
            if (fail.get())
                throw new IllegalStateException("Awaria zegara");
            return CLOCK.instant();
        });
        var document = document(1, clock);
        var before = document.snapshot();
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> document.submit(AUTHOR, document.snapshot().version()));
        assertSame(before, document.snapshot());
        fail.set(false);
        document.submit(AUTHOR, document.snapshot().version());
        before = document.snapshot();
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> document.approve(REVIEWER, document.snapshot().version(),
                document.reviewToken().orElseThrow()));
        assertSame(before, document.snapshot());
    }

    @Test
    void sequenceStillOrdersHistoryIfClockMovesBackwards() {
        var reads = new AtomicInteger();
        var document = document(1, clock(() -> CLOCK.instant().minusSeconds(reads.getAndIncrement())));
        document.submit(AUTHOR, document.snapshot().version());
        assertTrue(document.snapshot().history().getLast().at().isBefore(document.snapshot().history().getFirst()
                .at()));
        assertEquals(List.of(1L, 2L), document.snapshot().history().stream().map(WorkflowEvent::sequence).toList());
    }

    @Test
    void invalidValuesAndDefinitionsAreRejectedBeforeUse() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentContent(" ", "body"));
        assertThrows(IllegalArgumentException.class, () -> new DocumentContent("title", " "));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(List.of(new ReviewStage("same",
                Role.REVIEWER), new ReviewStage("same", Role.MANAGER))));
        assertThrows(IllegalArgumentException.class, () -> new ReviewStage("author", Role.AUTHOR));
        assertThrows(IllegalArgumentException.class, () -> new ReviewStage("Bad Stage", Role.REVIEWER));
        assertThrows(IllegalArgumentException.class, () -> new UserId(" "));
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(" "));
    }

    @Test
    void generatedRejectionRoundsPreserveFrozenRevisionsAndRequireAllApprovalsAgain() {
        var random = new Random(120021L);
        // 100 dokumentów z 1..5 odrzuceniami. Ziarno ustala wybór odrzuconego
        // etapu; nie jest to wyczerpujący dowód wszystkich sekwencji poleceń.
        for (int scenario = 0; scenario < 100; scenario++) {
            var document = document(2, CLOCK);
            int rejections = 1 + random.nextInt(5);
            for (int round = 1; round <= rejections; round++) {
                document.submit(AUTHOR, document.snapshot().version());
                assertEquals(round, document.snapshot().review().orElseThrow().number());
                assertTrue(document.snapshot().review().orElseThrow().approvals().isEmpty());
                if (random.nextBoolean()) {
                    document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
                    document.reject(MANAGER, document.snapshot().version(), document.reviewToken().orElseThrow(),
                            "Popraw runda " + round);
                } else
                    document.reject(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow(),
                            "Popraw runda " + round);
                var rejected = document.snapshot();
                document.edit(AUTHOR, document.snapshot().version(), new DocumentContent("Projekt",
                        "Poprawiona runda " + round));
                assertEquals(round, rejected.revision());
                assertEquals(round + 1, document.snapshot().revision());
                assertEquals(rejected.content(), document.snapshot().review().orElseThrow().submittedContent());
            }
            document.submit(AUTHOR, document.snapshot().version());
            document.approve(REVIEWER, document.snapshot().version(), document.reviewToken().orElseThrow());
            assertEquals(IN_REVIEW, document.snapshot().status());
            document.approve(MANAGER, document.snapshot().version(), document.reviewToken().orElseThrow());
            assertEquals(APPROVED, document.snapshot().status());
            assertEquals(rejections + 1, document.snapshot().review().orElseThrow().number());
        }
    }

    private static Document document(int stages, Clock clock) {
        return new Document(new DocumentId("DOC"), AUTHOR, CONTENT, plan(stages), clock);
    }

    private static WorkflowDefinition plan(int count) {
        var first = new ReviewStage("technical", Role.REVIEWER);
        return new WorkflowDefinition(count == 1 ? List.of(first) : List.of(first, new ReviewStage("manager",
                Role.MANAGER)));
    }

    private static Clock clock(java.util.function.Supplier<Instant> source) {
        return new Clock() {

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return source.get();
            }
        };
    }

    /**
     * Odmowa ma zachować cały snapshot, nie tylko jego status.
     */
    private static void reject(Document document, Runnable command, WorkflowException.Reason reason) {
        var before = document.snapshot();
        assertEquals(reason, assertThrows(WorkflowException.class, command::run).reason());
        assertSame(before, document.snapshot());
    }
}
