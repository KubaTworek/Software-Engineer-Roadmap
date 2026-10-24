package pl.jakubtworek.lld.workflow.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import static pl.jakubtworek.lld.workflow.domain.DocumentStatus.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowException.Reason.*;
import static pl.jakubtworek.lld.workflow.domain.WorkflowCommand.Operation.*;

/**
 * Właściciel reguł jednej instancji dokumentu, obsługiwanej w jednym wątku.
 * Każda mutacja wymaga expectedVersion. Guard jest wspólny dla dostępności,
 * oceny konkretnego polecenia i wykonania, a execute ponownie ocenia intencję
 * bezpośrednio przed zmianą. Odczyt assessment nie jest rezerwacją uprawnienia.
 * Pojedyncza publikacja nie gwarantuje atomowej obsługi równoległych wywołań.
 */
public final class Document {

    private final WorkflowDefinition definition;
    private final Clock clock;
    private final ReviewerPolicy reviewerPolicy;
    private DocumentSnapshot snapshot;

    public Document(DocumentId id, Actor author, DocumentContent content, WorkflowDefinition definition, Clock clock) {
        this(id, author, content, definition, clock, ReviewerPolicies.ALLOW_REUSE);
    }

    public Document(DocumentId id, Actor author, DocumentContent content, WorkflowDefinition definition, Clock clock,
            ReviewerPolicy reviewerPolicy) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(author);
        Objects.requireNonNull(content);
        this.definition = Objects.requireNonNull(definition);
        this.clock = Objects.requireNonNull(clock);
        this.reviewerPolicy = Objects.requireNonNull(reviewerPolicy);
        if (!author.has(Role.AUTHOR))
            throw new WorkflowException(FORBIDDEN);
        Instant now = clock.instant();
        var created = new WorkflowEvent(1, now, author.id(), WorkflowEvent.Type.CREATED, 1, 0, Optional.empty(),
                Optional.empty(), DRAFT);
        snapshot = new DocumentSnapshot(id, author.id(), content, 1, DRAFT, Optional.empty(), List.of(created), 1, 0,
                List.of(new ContentRevision(1, content, author.id(), now)));
    }

    public DocumentSnapshot snapshot() {
        return snapshot;
    }

    public WorkflowDefinition definition() {
        return definition;
    }

    public Optional<ContentRevision> archivedRevision(long revision) {
        if (revision < 1 || revision > snapshot.revisions().size())
            return Optional.empty();
        return Optional.of(snapshot.revisions().get((int) revision - 1));
    }

    public Optional<ReviewStage> currentStage() {
        return snapshot.status() == IN_REVIEW ? Optional.of(definition.stages().get(snapshot.review().orElseThrow()
                .approvals().size())) : Optional.empty();
    }

    public Optional<ReviewToken> reviewToken() {
        return currentStage().map(stage -> new ReviewToken(snapshot.id(), snapshot.review().orElseThrow().number(),
                snapshot.review().orElseThrow().submittedRevision(), stage.id()));
    }

    /**
     * Możliwość przygotowania polecenia przy aktualnych rolach i stanie.
     * EDIT nie zna przyszłego tekstu, REJECT/WITHDRAW nie znają uzasadnienia.
     * Te dodatkowe dane rozstrzyga assess, a nie osobna kopia reguł w adapterze.
     */
    public OperationAvailability availableOperations(Actor actor) {
        var operations = new EnumMap<WorkflowCommand.Operation, CommandAssessment>(WorkflowCommand.Operation.class);
        long version = snapshot.version();
        for (var operation : WorkflowCommand.Operation.values()) {
            operations.put(operation, guard(actor, version, operation, reviewToken()));
        }
        return new OperationAvailability(version, operations);
    }

    public CommandAssessment assess(Actor actor, long expectedVersion, WorkflowCommand command) {
        Objects.requireNonNull(command);
        CommandAssessment common = guard(actor, expectedVersion, command.operation(), command.token());
        if (!common.allowed())
            return common;
        return switch(command) {
            case WorkflowCommand.Edit edit ->
                snapshot.content()
                        .equals(edit.content()) ? CommandAssessment.deny(UNCHANGED_CONTENT) : CommandAssessment.permit();
            case WorkflowCommand.Reject reject ->
                reasonAssessment(reject.reason());
            case WorkflowCommand.Withdraw withdraw ->
                reasonAssessment(withdraw.reason());
            case WorkflowCommand.Submit ignored ->
                CommandAssessment.permit();
            case WorkflowCommand.Approve ignored ->
                CommandAssessment.permit();
        };
    }

    public DocumentSnapshot edit(Actor actor, long expectedVersion, DocumentContent content) {
        return execute(actor, expectedVersion, new WorkflowCommand.Edit(content));
    }

    public DocumentSnapshot submit(Actor actor, long expectedVersion) {
        return execute(actor, expectedVersion, new WorkflowCommand.Submit());
    }

    public DocumentSnapshot approve(Actor actor, long expectedVersion, ReviewToken token) {
        return execute(actor, expectedVersion, new WorkflowCommand.Approve(token));
    }

    public DocumentSnapshot reject(Actor actor, long expectedVersion, ReviewToken token, String reason) {
        return execute(actor, expectedVersion, new WorkflowCommand.Reject(token, reason));
    }

    public DocumentSnapshot withdraw(Actor actor, long expectedVersion, String reason) {
        return execute(actor, expectedVersion, new WorkflowCommand.Withdraw(reason));
    }

    /**
     * Jedyna ścieżka mutacji: sprawdzenie intencji, przygotowanie wszystkich
     * wartości lokalnie, publikacja całego kandydata. Nie zmieniamy najpierw
     * statusu, bo awaria Clock lub konstrukcji historii zostawiłaby pół operacji.
     * Przyjęta komenda zwiększa wersję raz; tylko Edit zwiększa rewizję treści.
     */
    public DocumentSnapshot execute(Actor actor, long expectedVersion, WorkflowCommand command) {
        CommandAssessment assessment = assess(actor, expectedVersion, command);
        assessment.refusal().ifPresent(reason -> {
            throw new WorkflowException(reason);
        });
        // Jeden odczyt po odmowach reguł; odczyty raportów nie używają Clock.
        Instant now = clock.instant();
        DocumentContent content = snapshot.content();
        long revision = snapshot.revision();
        int lastRound = snapshot.lastRound();
        DocumentStatus status = snapshot.status();
        Optional<ReviewRound> review = snapshot.review();
        var revisions = snapshot.revisions();
        WorkflowEvent.Type type;
        String stage = null, note = null;
        switch(command) {
            case WorkflowCommand.Edit edit ->
                {
                    content = edit.content();
                    revision = Math.incrementExact(revision);
                    revisions = HistoryVector.append(revisions, new ContentRevision(revision, content, actor.id(),
                            now));
                    type = WorkflowEvent.Type.EDITED;
                }
            case WorkflowCommand.Submit ignored ->
                {
                    lastRound = Math.incrementExact(lastRound);
                    review = Optional.of(new ReviewRound(lastRound, revision, content, List.of()));
                    status = IN_REVIEW;
                    type = WorkflowEvent.Type.SUBMITTED;
                    stage = definition.stages().getFirst().id();
                }
            case WorkflowCommand.Approve ignored ->
                {
                    stage = currentStage().orElseThrow().id();
                    ReviewRound old = review.orElseThrow();
                    var approvals = new ArrayList<>(old.approvals());
                    approvals.add(new StageApproval(stage, actor.id(), now));
                    review = Optional.of(new ReviewRound(old.number(), old.submittedRevision(), old.submittedContent(),
                            approvals));
                    status = approvals.size() == definition.stages().size() ? APPROVED : IN_REVIEW;
                    type = WorkflowEvent.Type.STAGE_APPROVED;
                }
            case WorkflowCommand.Reject reject ->
                {
                    stage = currentStage().orElseThrow().id();
                    note = reject.reason().strip();
                    status = REJECTED;
                    type = WorkflowEvent.Type.REJECTED;
                }
            case WorkflowCommand.Withdraw withdraw ->
                {
                    stage = currentStage().orElseThrow().id();
                    note = withdraw.reason().strip();
                    review = Optional.empty();
                    status = DRAFT;
                    type = WorkflowEvent.Type.WITHDRAWN;
                    // lastRound nie zeruje się: resubmit tej samej treści musi dostać NOWĄ rundę.
                }
        }
        long version = Math.incrementExact(snapshot.version());
        long sequence = Math.incrementExact(snapshot.history().getLast().sequence());
        var history = HistoryVector.append(snapshot.history(), new WorkflowEvent(sequence, now, actor.id(), type,
                revision, lastRound, Optional.ofNullable(stage), Optional.ofNullable(note), status));
        var candidate = new DocumentSnapshot(snapshot.id(), snapshot.author(), content, revision, status, review,
                history, version, lastRound, revisions);
        // Historia, archiwum, liczniki i stan stają się widoczne razem.
        snapshot = candidate;
        return candidate;
    }

    /**
     * Kolejność odmów jest częścią kontraktu, a nie przypadkiem parsera konsoli.
     * Najpierw odrzucamy nieaktualny widok; potem własność/stan albo kontekst
     * decyzji. Polityka recenzentów uzupełnia rolę i zakaz autora, nie zastępuje ich.
     * Współdzielenie tej metody zapobiega rozjazdowi raportu i wykonania.
     */
    private CommandAssessment guard(Actor actor, long expectedVersion, WorkflowCommand.Operation operation,
            Optional<ReviewToken> token) {
        Objects.requireNonNull(actor);
        if (expectedVersion != snapshot.version())
            return CommandAssessment.deny(STALE_VERSION);
        if (operation == EDIT || operation == SUBMIT || operation == WITHDRAW) {
            if (!actor.has(Role.AUTHOR) || !actor.id().equals(snapshot.author()))
                return CommandAssessment.deny(FORBIDDEN);
            if (operation == WITHDRAW)
                return snapshot.status() == IN_REVIEW ? CommandAssessment.permit() : CommandAssessment.deny(WRONG_STATE);
            if (snapshot.status() != DRAFT && snapshot.status() != REJECTED)
                return CommandAssessment.deny(WRONG_STATE);
            if (operation == SUBMIT && snapshot.status() == REJECTED && snapshot.revision() <= snapshot.review()
                    .orElseThrow().submittedRevision()) {
                return CommandAssessment.deny(NEW_REVISION_REQUIRED);
            }
            return CommandAssessment.permit();
        }
        if (snapshot.status() != IN_REVIEW)
            return CommandAssessment.deny(WRONG_STATE);
        if (!reviewToken().equals(token))
            return CommandAssessment.deny(STALE_REVIEW);
        if (actor.id().equals(snapshot.author()))
            return CommandAssessment.deny(SELF_REVIEW);
        if (!actor.has(currentStage().orElseThrow().requiredRole()))
            return CommandAssessment.deny(FORBIDDEN);
        if (!reviewerPolicy.permits(actor.id(), snapshot.review().orElseThrow()))
            return CommandAssessment.deny(REVIEWER_ALREADY_USED);
        return CommandAssessment.permit();
    }

    private CommandAssessment reasonAssessment(String reason) {
        return reason == null
                || reason.isBlank() ? CommandAssessment.deny(REASON_REQUIRED) : CommandAssessment.permit();
    }
}
