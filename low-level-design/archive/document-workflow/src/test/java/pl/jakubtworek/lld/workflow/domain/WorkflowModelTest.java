package pl.jakubtworek.lld.workflow.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowEnhancementsTest.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowException.Reason.*;

/**
 * Niezależna wyrocznia używa liczb, tekstu i tabeli przejść, bez wywoływania
 * walidatorów Document. Ziarno odtwarza błąd, a opis kroku lokalizuje najkrótszy
 * znany prefiks. To skończone próbkowanie historii, nie dowód wszystkich sekwencji.
 */
class WorkflowModelTest {

    @ParameterizedTest
    @EnumSource(ReviewerPolicies.class)
    void generatedCommandsAgreeWithIndependentModel(ReviewerPolicies policy) {
        var random = new Random(120021L);
        var actors = List.of(AUTHOR, BOTH, OTHER, actor("bob", Role.REVIEWER), actor("guest"), actor("outsider",
                Role.AUTHOR));
        var successes = EnumSet.noneOf(WorkflowCommand.Operation.class);
        var refusals = EnumSet.noneOf(WorkflowException.Reason.class);
        for (int scenario = 0; scenario < 150; scenario++) {
            var d = document(policy);
            var m = new Model();
            var tokens = new ArrayList<ReviewToken>();
            for (int step = 0; step < 100; step++) {
                // Zatwierdzenie jest terminalne; nowa historia zacznie od DRAFT.
                if (m.state == 3)
                    break;
                Actor actor = actors.get(random.nextInt(actors.size()));
                long version = random.nextInt(6) == 0 ? m.version - 1 : m.version;
                ReviewToken token = m.token();
                tokens.add(token);
                if (random.nextInt(4) == 0)
                    token = tokens.get(random.nextInt(tokens.size()));
                String reason = random.nextInt(4) == 0 ? " " : "powód";
                WorkflowCommand command = switch(random.nextInt(5)) {
                    case 0 ->
                        new WorkflowCommand.Edit(new DocumentContent("T",
                                random.nextBoolean() ? m.body : "tekst-" + step));
                    case 1 ->
                        new WorkflowCommand.Submit();
                    case 2 ->
                        new WorkflowCommand.Approve(token);
                    case 3 ->
                        new WorkflowCommand.Reject(token, reason);
                    default ->
                        new WorkflowCommand.Withdraw(reason);
                };
                String context = policy + " scenario=" + scenario + " step=" + step + " " + command;
                // Dla raportu podstawiamy dane na pewno poprawne: niezmienność
                // tekstu i puste reason są dopiero sprawami konkretnej intencji.
                var available = d.availableOperations(actor);
                assertEquals(m.version, available.version(), context);
                for (WorkflowCommand probe : List.of(new WorkflowCommand.Edit(new DocumentContent("T",
                        "availability-never-written")), new WorkflowCommand.Submit(),
                        new WorkflowCommand.Approve(m.token()), new WorkflowCommand.Reject(m.token(), "ok"),
                        new WorkflowCommand.Withdraw("ok"))) {
                    assertEquals(Optional.ofNullable(m.refusal(actor, m.version, probe, policy)), available.operations()
                            .get(probe.operation()).refusal(), context);
                }
                var before = d.snapshot();
                var expected = m.refusal(actor, version, command, policy);
                assertEquals(Optional.ofNullable(expected), d.assess(actor, version, command).refusal(), context);
                if (expected != null) {
                    refusals.add(expected);
                    assertEquals(expected, assertThrows(WorkflowException.class, () -> d.execute(actor, version,
                            command), context).reason(), context);
                    assertSame(before, d.snapshot(), context);
                } else {
                    successes.add(command.operation());
                    m.accept(actor, command);
                    d.execute(actor, version, command);
                }
                var view = d.snapshot();
                assertEquals(List.of(DocumentStatus.DRAFT, DocumentStatus.IN_REVIEW, DocumentStatus.REJECTED,
                        DocumentStatus.APPROVED).get(m.state), view.status(), context);
                assertEquals(m.version, view.version(), context);
                assertEquals(m.revision, view.revision(), context);
                assertEquals(m.round, view.lastRound(), context);
                assertEquals(m.version, view.history().size(), context);
                assertEquals(m.archive, view.revisions().stream().map(r -> r.content().body()).toList(), context);
                assertEquals(m.body, view.content().body(), context);
                assertEquals(m.state != 0, view.review().isPresent(), context);
                if (view.review().isPresent()) {
                    assertEquals(m.submittedRevision, view.review().orElseThrow().submittedRevision(), context);
                    assertEquals(m.archive.get((int) m.submittedRevision - 1), view.review().orElseThrow()
                            .submittedContent().body(), context);
                    assertEquals(m.approvers, view.review().orElseThrow().approvals().stream().map(a -> a.reviewer()
                            .value()).toList(), context);
                }
                assertEquals(m.state == 1 ? Optional.of(m.token()) : Optional.empty(), d.reviewToken(), context);
            }
        }
        assertEquals(EnumSet.allOf(WorkflowCommand.Operation.class), successes);
        assertTrue(refusals.containsAll(Set.of(STALE_VERSION, STALE_REVIEW, SELF_REVIEW, FORBIDDEN, WRONG_STATE,
                NEW_REVISION_REQUIRED, UNCHANGED_CONTENT, REASON_REQUIRED)));
        assertEquals(policy == ReviewerPolicies.DISTINCT_PER_ROUND, refusals.contains(REVIEWER_ALREADY_USED));
    }

    private static final class Model {

        // 0=DRAFT, 1=IN_REVIEW, 2=REJECTED, 3=APPROVED. Dane nie pochodzą ze snapshotu SUT.
        int state, round;
        long version = 1, revision = 1, submittedRevision;
        String body = "v1";
        final List<String> archive = new ArrayList<>(List.of("v1"));
        final List<String> approvers = new ArrayList<>();

        ReviewToken token() {
            return new ReviewToken(new DocumentId("DOC"), Math.max(1, round), Math.max(1, submittedRevision),
                    approvers.isEmpty() ? "technical" : "manager");
        }

        WorkflowException.Reason refusal(Actor actor, long expectedVersion, WorkflowCommand c,
                ReviewerPolicies policy) {
            if (expectedVersion != version)
                return STALE_VERSION;
            boolean authorOperation = switch(c.operation()) {
                case EDIT, SUBMIT, WITHDRAW ->
                    true;
                default ->
                    false;
            };
            if (authorOperation) {
                if (!actor.id().value().equals("alice") || !actor.roles().contains(Role.AUTHOR))
                    return FORBIDDEN;
                if (c instanceof WorkflowCommand.Withdraw) {
                    if (state != 1)
                        return WRONG_STATE;
                } else {
                    if (state != 0 && state != 2)
                        return WRONG_STATE;
                    if (c instanceof WorkflowCommand.Submit && state == 2 && revision == submittedRevision)
                        return NEW_REVISION_REQUIRED;
                }
            } else {
                if (state != 1)
                    return WRONG_STATE;
                if (!c.token().equals(Optional.of(token())))
                    return STALE_REVIEW;
                if (actor.id().value().equals("alice"))
                    return SELF_REVIEW;
                Role required = approvers.isEmpty() ? Role.REVIEWER : Role.MANAGER;
                if (!actor.roles().contains(required))
                    return FORBIDDEN;
                if (policy == ReviewerPolicies.DISTINCT_PER_ROUND && approvers.contains(actor.id().value()))
                    return REVIEWER_ALREADY_USED;
            }
            if (c instanceof WorkflowCommand.Edit e && e.content().body().equals(body))
                return UNCHANGED_CONTENT;
            if (c instanceof WorkflowCommand.Reject r && r.reason().isBlank())
                return REASON_REQUIRED;
            if (c instanceof WorkflowCommand.Withdraw w && w.reason().isBlank())
                return REASON_REQUIRED;
            return null;
        }

        void accept(Actor actor, WorkflowCommand c) {
            version++;
            switch(c) {
                case WorkflowCommand.Edit e ->
                    {
                        body = e.content().body();
                        revision++;
                        archive.add(body);
                    }
                case WorkflowCommand.Submit ignored ->
                    {
                        state = 1;
                        round++;
                        submittedRevision = revision;
                        approvers.clear();
                    }
                case WorkflowCommand.Approve ignored ->
                    {
                        approvers.add(actor.id().value());
                        if (approvers.size() == 2)
                            state = 3;
                    }
                case WorkflowCommand.Reject ignored ->
                    state = 2;
                case WorkflowCommand.Withdraw ignored ->
                    {
                        state = 0;
                        approvers.clear();
                        submittedRevision = 0;
                    }
            }
        }
    }
}
